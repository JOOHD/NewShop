package JOO.jooshop.cart.model;

import JOO.jooshop.cart.entity.Cart;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.productManagement.entity.ProductManagement;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 장바구니 단건 응답 DTO
 */
@Getter
public class CartDto {

    private final Long cartId;
    private final Long memberId;
    private final String memberName;
    private final String phoneNumber;
    private final String productName;
    private final String size;
    private final BigDecimal originalPrice;
    private final int discountRate;
    private final BigDecimal finalPrice;
    private final int quantity;
    private final BigDecimal totalPrice;
    private final String productThumbnailUrl;

    public CartDto(Long cartId,
                   Long memberId,
                   String memberName,
                   String phoneNumber,
                   String productName,
                   String size,
                   BigDecimal originalPrice,
                   int discountRate,
                   BigDecimal finalPrice,
                   int quantity,
                   BigDecimal totalPrice,
                   String productThumbnailUrl) {
        this.cartId = cartId;
        this.memberId = memberId;
        this.memberName = memberName;
        this.phoneNumber = phoneNumber;
        this.productName = productName;
        this.size = size;
        this.originalPrice = originalPrice;
        this.discountRate = discountRate;
        this.finalPrice = finalPrice;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.productThumbnailUrl = productThumbnailUrl;
    }

    /**
     * Cart 엔티티를 장바구니 응답 DTO로 변환
     */
    public static CartDto toDto(Cart cart) {
        ProductManagement productManagement = cart.getProductManagement();
        Product product = productManagement.getProduct();

        int quantity = cart.getQuantity();
        BigDecimal originalPrice = product.getPrice();
        int discountRate = extractDiscountRate(product);
        BigDecimal finalPrice = calculateDiscountedPrice(originalPrice, discountRate);
        BigDecimal totalPrice = finalPrice.multiply(BigDecimal.valueOf(quantity));
        String thumbnailUrl = extractThumbnailUrl(product);
        String size = extractSize(productManagement);

        return new CartDto(
                cart.getCartId(),
                cart.getMember().getId(),
                cart.getMember().getUsername(),
                cart.getMember().getPhoneNumber(),
                product.getProductName(),
                size,
                originalPrice,
                discountRate,
                finalPrice,
                quantity,
                totalPrice,
                thumbnailUrl
        );
    }

    /**
     * 할인율이 없으면 0으로 처리
     */
    private static int extractDiscountRate(Product product) {
        return product.getDiscountRate() != null ? product.getDiscountRate() : 0;
    }

    /**
     * 원가에 할인율을 적용한 단가 계산
     */
    private static BigDecimal calculateDiscountedPrice(BigDecimal originalPrice, int discountRate) {
        BigDecimal discountMultiplier = BigDecimal.valueOf(100 - discountRate)
                .divide(BigDecimal.valueOf(100));
        return originalPrice.multiply(discountMultiplier);
    }

    /**
     * 대표 썸네일 URL 추출
     */
    private static String extractThumbnailUrl(Product product) {
        List<?> thumbnails = product.getProductThumbnails();
        return thumbnails == null || thumbnails.isEmpty()
                ? null
                : product.getProductThumbnails().get(0).getImagesPath();
    }

    /**
     * 옵션 사이즈 문자열 추출
     */
    private static String extractSize(ProductManagement productManagement) {
        return productManagement.getSize() != null
                ? productManagement.getSize().name()
                : null;
    }
}