package JOO.jooshop.payment.model;

import JOO.jooshop.payment.entity.PaymentHistory;
import JOO.jooshop.payment.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
 * [PaymentHistoryDto]
 * 기존 -> toEntity() 네이밍 오류, statusType 사용
 * 리팩토링 -> 조회 응답 DTO 로만 사용하고 from() 으로 통일,
 *            paymentStatus 단일 필드 기준으로 매핑
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryDto {

    private Long paymentId;
    private Long memberId;
    private Long orderId;

    private Long productId;
    private String productName;
    private String ImagesPath;
    private BigDecimal productPrice;
    private int productQuantity;
    private String option;

    private String merchantUid;
    private String ordererName;
    private String phoneNumber;
    private boolean discount;
    private String buyerAddr;
    private LocalDateTime orderedAt;
    private BigDecimal totalPrice;
    private String payMethod;
    private String bankName;
    private LocalDateTime paidAt;
    private PaymentStatus paymentStatus;
    private boolean review;

    public static PaymentHistoryDto from(PaymentHistory paymentHistory) {
        return new PaymentHistoryDto(
                paymentHistory.getId(),
                paymentHistory.getMember().getId(),
                paymentHistory.getOrders().getOrderId(),

                paymentHistory.getProduct().getProductId(),
                paymentHistory.getProductName(),
                paymentHistory.getFirstThumbnailImagesPath(),
                paymentHistory.getPrice(),
                paymentHistory.getQuantity(),
                paymentHistory.getProductOption(),

                paymentHistory.getOrders().getMerchantUid(),
                paymentHistory.getOrders().getOrdererName(),
                paymentHistory.getOrders().getPhoneNumber(),
                paymentHistory.getProduct().isDiscount(),
                paymentHistory.getBuyerAddr(),
                paymentHistory.getOrders().getOrderDay(),
                paymentHistory.getTotalPrice(),
                paymentHistory.getPayMethod(),
                paymentHistory.getBankName(),
                paymentHistory.getPaidAt(),
                paymentHistory.getPaymentStatus(),
                paymentHistory.isReview()
        );
    }
}