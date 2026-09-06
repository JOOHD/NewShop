package JOO.jooshop.cart.entity;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.productVariant.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cart")
public class Cart {
    /*
     * [Entity]
     *
     * 기존
     * - Cart를 단순 장바구니 데이터 저장 엔티티로 사용
     * - 수량 변경 / 중복 상품 처리 같은 규칙이 서비스에 치우칠 수 있었음
     * - 장바구니를 도메인 객체라기보다 임시 저장 데이터처럼 다룰 가능성이 있었음
     *
     * refactoring 26.04
     * - Cart는 회원이 선택한 상품 옵션과 수량을 보관하는 장바구니 엔티티
     * - Member와 ProductVariant를 참조하여
     *   "누가 어떤 옵션 상품을 몇 개 담았는지"를 표현
     * - createCart()를 통해 생성 책임을 엔티티 내부로 이동
     * - replaceQuantity(), increaseQuantity() 등
     *   수량 변경 책임을 엔티티 내부 도메인 메서드로 관리
     * - 동일 회원 + 동일 상품 옵션 조합 여부는 서비스/리포지토리에서 판별하고
     *   상태 변경은 Cart가 직접 수행하도록 설계
     * - 장바구니 상태 변경 규칙을 엔티티 중심으로 정리
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id")
    private Long cartId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_mgt_id", nullable = false)
    private ProductVariant productVariant;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    /**
     * Cart 생성 시 불변식 검증 및 가격 자동 계산
     */
    private Cart(Member member, ProductVariant productVariant, int quantity) {
        validateCreate(member, productVariant, quantity);

        this.member = member;
        this.productVariant = productVariant;
        this.quantity = quantity;
        this.price = calculatePrice(productVariant, quantity);
    }

    /**
     * 장바구니 생성 팩토리 메서드 (외부에서 생성자 직접 호출 방지)
     */
    public static Cart createCart(Member member, ProductVariant productVariant, int quantity) {
        return new Cart(member, productVariant, quantity);
    }

    /**
     * 장바구니 수량 변경 + 가격 재계산
     */
    public void changeQuantity(int quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
        this.price = calculatePrice(this.productVariant, quantity);
    }

    /**
     * 기존 장바구니 존재 시 수량 덮어쓰기
     */
    public void replaceQuantity(int quantity) {
        changeQuantity(quantity);
    }

    /**
     * 현재 로그인 사용자와 장바구니 소유자 일치 여부 확인
     */
    public boolean isOwnedBy(Long memberId) {
        if (memberId == null) {
            return false;
        }
        return Objects.equals(this.member.getId(), memberId);
    }

    /**
     * 장바구니 소유자 ID 반환 (권한 검증용)
     */
    public Long getOwnerId() {
        return this.member.getId();
    }

    /**
     * 생성 시 필수 값 검증
     */
    private static void validateCreate(Member member, ProductVariant productVariant, int quantity) {
        if (member == null) {
            throw new IllegalArgumentException("회원은 null일 수 없습니다.");
        }
        if (productVariant == null) {
            throw new IllegalArgumentException("상품 옵션은 null일 수 없습니다.");
        }
        validateQuantity(quantity);
        validateUnitPrice(productVariant);
    }

    /**
     * 수량 유효성 검증 (1개 이상)
     */
    private static void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1개 이상이어야 합니다.");
        }
    }

    /**
     * 상품 가격 존재 여부 검증
     */
    private static void validateUnitPrice(ProductVariant productVariant) {
        Product product = productVariant.getProduct();
        if (product == null) {
            throw new IllegalArgumentException("상품 정보가 존재하지 않습니다.");
        }
        if (product.getPrice() == null) {
            throw new IllegalArgumentException("상품 가격이 존재하지 않습니다.");
        }
    }

    /**
     * 단가 * 수량으로 총 가격 계산
     */
    private static BigDecimal calculatePrice(ProductVariant productVariant, int quantity) {
        return productVariant.getProduct().getPrice()
                .multiply(BigDecimal.valueOf(quantity));
    }
}