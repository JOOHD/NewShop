package JOO.jooshop.order.entity;

import JOO.jooshop.productVariant.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = {"orders", "productVariant"})
@Table(name = "order_product")
public class OrderProduct {

    /*
     * [Entity]
     *
     * 기존
     * - OrderProduct를 주문 상품 엔티티로 사용했지만,
     *   "주문 당시 확정 정보 보존" 역할이 주석/구조상 충분히 드러나지 않을 수 있었음
     * - Orders 와의 연관관계는 존재하지만,
     *   Aggregate 내부 자식 엔티티라는 의도가 코드 설명에서 약했음
     * - 상품명 / 옵션 / 가격 / 이미지가 왜 별도 저장되는지
     *   즉, 스냅샷 개념이 명확하게 표현되지 않았음
     *
     * refactoring 26.04
     * - Orders = Aggregate Root
     * - OrderProduct = Orders의 자식 엔티티
     * - 주문 당시 상품 스냅샷 보존
     *   (상품명, 옵션, 이미지, 주문 가격, 수량)
     * - Product / ProductVariant의 현재 값이 아니라
     *   주문 시점의 확정 정보를 별도로 저장
     * - 주문 이후 상품 정보가 변경되어도
     *   주문 이력은 변하지 않도록 설계
     * - 연관관계 연결은 Orders 내부 addOrderProduct()에서 관리
     * - attachTo()는 Aggregate 내부에서만 호출되는 연결 메서드
     * - calculateLineTotal()로 주문 상품 1건의 금액 계산 책임을 가짐
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orders_id", nullable = false)
    private Orders orders;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_management_id", nullable = false)
    private ProductVariant productVariant;

    @Column(name = "price_at_order", nullable = false)
    private BigDecimal priceAtOrder;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_size")
    private String productSize;

    @Column(name = "product_img")
    private String productImg;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "reviewed", nullable = false)
    private boolean reviewed;

    @Column(name = "returned", nullable = false)
    private boolean returned;

    private OrderProduct(
            ProductVariant productVariant,
            String productName,
            String productSize,
            String productImg,
            BigDecimal priceAtOrder,
            int quantity
    ) {
        if (productVariant == null) throw new IllegalArgumentException("상품 옵션은 필수입니다.");
        if (priceAtOrder == null || priceAtOrder.signum() < 0) {
            throw new IllegalArgumentException("주문 당시 가격은 0 이상이어야 합니다.");
        }
        if (quantity <= 0) throw new IllegalArgumentException("주문 수량은 1 이상이어야 합니다.");

        this.productVariant = productVariant;
        this.productName = productName;
        this.productSize = productSize;
        this.productImg = productImg;
        this.priceAtOrder = priceAtOrder;
        this.quantity = quantity;
        this.reviewed = false;
        this.returned = false;
    }

    public static OrderProduct createOrderProduct(
            ProductVariant productVariant,
            String productName,
            String productSize,
            String productImg,
            BigDecimal priceAtOrder,
            int quantity
    ) {
        return new OrderProduct(
                productVariant,
                productName,
                productSize,
                productImg,
                priceAtOrder,
                quantity
        );
    }

    void attachTo(Orders orders) {
        this.orders = orders;
    }

    public void completeReview() {
        this.reviewed = true;
    }

    public void markReturned() {
        this.returned = true;
    }

    public BigDecimal calculateLineTotal() {
        return this.priceAtOrder.multiply(BigDecimal.valueOf(this.quantity));
    }
}