package JOO.jooshop.order.entity;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.order.entity.enums.PayMethod;
import JOO.jooshop.payment.entity.PaymentHistory;
import JOO.jooshop.payment.entity.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자 — 외부 직접 생성 방지
@Table(name = "orders")
public class Orders {

    /*
     * [Entity]
     *
     * 기존
     * - 주문 엔티티로 사용되었지만,
     *   주문 생성 / 주문 상품 연결 / 합계 계산 책임이 구조상 명확히 드러나지 않았음
     * - OrderProduct와 연관관계는 있었으나
     *   주문이 자식 엔티티를 관리하는 Aggregate Root라는 의도가 약했음
     * - 주문 생성 과정에서 서비스 레이어가 세부 생성 책임을 많이 가질 가능성이 있었음
     *
     * refactoring 26.04
     * - Orders = 주문 도메인의 Aggregate Root
     * - 주문자 정보, 배송 정보, 결제 수단, 주문 상태 등 주문의 핵심 상태를 관리
     * - createOrder()를 통해 생성 책임을 엔티티 내부로 이동
     * - addOrderProduct()를 통해 자식 엔티티(OrderProduct) 추가 및 연관관계 연결
     * - 주문 상품 목록 기준으로 총 주문 금액/수량 등 요약 값을 재계산
     * - 주문 관련 상태 변경은 의미 있는 도메인 메서드 중심으로 처리
     * - 주문 도메인의 진입점은 Orders이며,
     *   OrderProduct는 Orders를 통해서만 관리되도록 설계
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @OneToMany(mappedBy = "orders", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderProduct> orderProducts = new ArrayList<>();

    @Column(name = "order_name", nullable = false)
    private String ordererName;

    @Column(name = "product_name", nullable = false)
    private String productNameSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_method", nullable = false)
    private PayMethod payMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(length = 100, name = "merchant_uid", nullable = false, unique = true)
    private String merchantUid;

    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "detail_address")
    private String detailAddress;

    @Column(name = "post_code", length = 100, nullable = false)
    private String postCode;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime orderDay;

    @OneToMany(mappedBy = "orders")
    private List<PaymentHistory> paymentHistories = new ArrayList<>();

    // private (생성자 숨김) = 외부 클래스에서 new Order(...) 방지
    // 생성자는 "createOrder에서 받은 값으로 자기 자신을 채우는 곳"
    private Orders(
            Member member,          // createOrder 에서 받은 값 채워짐
            String ordererName,     // createOrder 에서 받은 값 채워짐
            String phoneNumber,     // createOrder 에서 받은 값 채워짐
            String postCode,
            String address,
            String detailAddress,
            PayMethod payMethod,
            String merchantUid
    ) {
        validateCreate(member, ordererName, phoneNumber, postCode, address, payMethod, merchantUid);

        this.member = member;
        this.ordererName = ordererName;
        this.phoneNumber = phoneNumber;
        this.postCode = postCode;
        this.address = address;
        this.detailAddress = detailAddress;
        this.payMethod = payMethod;
        this.merchantUid = merchantUid;
        this.paymentStatus = PaymentStatus.COMPLETE; // 현재 프로젝트 흐름 유지, 추후 READY로 변경 가능
        this.totalPrice = BigDecimal.ZERO;
        this.productNameSummary = "";
    }

    // 외부에 공개된 주문 생성 공식 입구
    public static Orders createOrder(
            // 여기 선언변수에 서비스에서 가져온,
            // createOrder(orderDto/tempOrder 가져온 값) 값을 넣어줌
            Member member,          // orderDto 에서 가져온 값
            String ordererName,     // orderDto 에서 가져온 값
            String phoneNumber,     // orderDto 에서 가져온 값...
            String postCode,
            String address,
            String detailAddress,
            PayMethod payMethod,
            String merchantUid
    ) {
        return new Orders(          // 1. new Orders 생성자 호출
                                    // 2. return 객체 반환
                member,
                ordererName,
                phoneNumber,
                postCode,
                address,
                detailAddress,
                payMethod,
                merchantUid
        );
    }

    public void addOrderProduct(OrderProduct orderProduct) {
        if (orderProduct == null) {
            throw new IllegalArgumentException("주문 상품은 null일 수 없습니다.");
        }

        this.orderProducts.add(orderProduct);
        orderProduct.attachTo(this);
        recalculateOrderSummary();
    }

    public void addOrderProducts(List<OrderProduct> orderProducts) {
        if (orderProducts == null || orderProducts.isEmpty()) {
            throw new IllegalArgumentException("주문 상품은 최소 1개 이상이어야 합니다.");
        }

        orderProducts.forEach(this::addOrderProduct); // = for (OrderProduct orderProduct : orderProducts)
    }

    public void changePaymentStatus(PaymentStatus paymentStatus) {
        if (paymentStatus == null) {
            throw new IllegalArgumentException("결제 상태는 null일 수 없습니다.");
        }
        this.paymentStatus = paymentStatus;
    }

    public void markPaid() {
        this.paymentStatus = PaymentStatus.COMPLETE;
    }

    public void markCanceled() {
        this.paymentStatus = PaymentStatus.CANCELED;
    }

    private void recalculateOrderSummary() {
        this.totalPrice = this.orderProducts.stream()
                .map(OrderProduct::calculateLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.productNameSummary = this.orderProducts.stream()
                .map(OrderProduct::getProductName)
                .reduce((first, second) -> first + "," + second)
                .orElse("");
    }

    private void validateCreate(
            Member member,
            String ordererName,
            String phoneNumber,
            String postCode,
            String address,
            PayMethod payMethod,
            String merchantUid
    ) {
        if (member == null) throw new IllegalArgumentException("주문 회원은 필수입니다.");
        if (isBlank(ordererName)) throw new IllegalArgumentException("주문자명은 필수입니다.");
        if (isBlank(phoneNumber)) throw new IllegalArgumentException("전화번호는 필수입니다.");
        if (isBlank(postCode)) throw new IllegalArgumentException("우편번호는 필수입니다.");
        if (isBlank(address)) throw new IllegalArgumentException("주소는 필수입니다.");
        if (payMethod == null) throw new IllegalArgumentException("결제수단은 필수입니다.");
        if (isBlank(merchantUid)) throw new IllegalArgumentException("merchantUid는 필수입니다.");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
