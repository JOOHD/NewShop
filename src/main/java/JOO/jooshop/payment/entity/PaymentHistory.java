package JOO.jooshop.payment.entity;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.order.entity.OrderProduct;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
 * [Entity]
 *
 * 기존
 * - 결제 이력 저장 필요성은 있었지만,
 *   주문 / 결제 / 주문상품 단위 기록 책임이 명확히 분리되지 않을 수 있었음
 *
 * refactoring 26.04
 * - PaymentHistory는 결제 이력을 남기는 기록용 엔티티
 * - 결제 완료 시점의 결과를 추적 가능하도록 저장
 * - 필요 시 주문 단위가 아니라 주문 상품(OrderProduct) 단위로도 이력 기록 가능
 * - Payment와 분리하여 감사 로그성 / 조회성 데이터 역할을 강화
 * - 결제 이후 환불, 반품, 이력 조회 등 확장 포인트를 고려한 구조
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payment_history")
public class PaymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orders", nullable = false)
    private Orders orders;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product", nullable = false)
    private Product product;

    @Column(name = "imp_uid", nullable = false)
    private String impUid;

    @Column(name = "pay_method", nullable = false)
    private String payMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "bank_code")
    private String bankCode;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "buyer_addr")
    private String buyerAddr;

    @Column(name = "buyer_email")
    private String buyerEmail;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_option")
    private String productOption;

    @Column(name = "product_price", nullable = false)
    private BigDecimal price;

    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "review", nullable = false)
    private boolean review;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    public static PaymentHistory createPaymentHistory(
            Member member,
            Orders orders,
            OrderProduct orderProduct,
            String impUid,
            String payMethod,
            BigDecimal totalPrice,
            String bankCode,
            String bankName,
            String buyerAddr,
            String buyerEmail
    ) {
        validateCreate(member, orders, orderProduct, impUid, payMethod, totalPrice);

        PaymentHistory paymentHistory = new PaymentHistory();
        paymentHistory.member = member;
        paymentHistory.orders = orders;
        paymentHistory.product = orderProduct.getProductVariant().getProduct();
        paymentHistory.impUid = impUid;
        paymentHistory.payMethod = payMethod;
        paymentHistory.paymentStatus = PaymentStatus.COMPLETE;
        paymentHistory.bankCode = bankCode;
        paymentHistory.bankName = bankName;
        paymentHistory.buyerAddr = buyerAddr;
        paymentHistory.buyerEmail = buyerEmail;
        paymentHistory.productName = orderProduct.getProductName();
        paymentHistory.productOption = orderProduct.getProductSize();
        paymentHistory.price = orderProduct.getPriceAtOrder();
        paymentHistory.totalPrice = totalPrice;
        paymentHistory.paidAt = LocalDateTime.now();
        paymentHistory.review = false;
        paymentHistory.quantity = orderProduct.getQuantity();
        return paymentHistory;
    }

    public void markCanceled() {
        if (this.paymentStatus == PaymentStatus.CANCELED) {
            throw new IllegalStateException("이미 취소된 결제입니다.");
        }
        this.paymentStatus = PaymentStatus.CANCELED;
    }

    public void markReviewed() {
        if (this.review) {
            throw new IllegalStateException("이미 리뷰 작성이 완료되었습니다.");
        }
        this.review = true;
    }

    public boolean isCancelable() {
        return this.paymentStatus.isCancelable();
    }

    public String getFirstThumbnailImagesPath() {
        if (product != null && !product.getProductThumbnails().isEmpty()) {
            return product.getProductThumbnails().get(0).getImagesPath();
        }
        return null;
    }

    private static void validateCreate(
            Member member,
            Orders orders,
            OrderProduct orderProduct,
            String impUid,
            String payMethod,
            BigDecimal totalPrice
    ) {
        if (member == null) {
            throw new IllegalArgumentException("회원은 필수입니다.");
        }
        if (orders == null) {
            throw new IllegalArgumentException("주문은 필수입니다.");
        }
        if (orderProduct == null) {
            throw new IllegalArgumentException("주문 상품은 필수입니다.");
        }
        if (impUid == null || impUid.isBlank()) {
            throw new IllegalArgumentException("impUid는 필수입니다.");
        }
        if (payMethod == null || payMethod.isBlank()) {
            throw new IllegalArgumentException("결제 수단은 필수입니다.");
        }
        if (totalPrice == null || totalPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("총 결제 금액이 올바르지 않습니다.");
        }
    }
}