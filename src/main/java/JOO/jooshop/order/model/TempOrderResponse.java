package JOO.jooshop.order.model;

import JOO.jooshop.order.entity.TemporaryOrderRedis;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 임시 주문 조회 응답 DTO — 읽기 전용, Setter 불필요. */
@Getter
public class TempOrderResponse {

    private Long memberId;
    private String ordererName;
    private String phoneNumber;

    private List<String> productNames = new ArrayList<>();
    private List<String> productSizes = new ArrayList<>();
    private List<String> productImages = new ArrayList<>();
    private List<Integer> productQuantities = new ArrayList<>();

    private BigDecimal totalPrice = BigDecimal.ZERO;

    // Entity → DTO 변환 생성자
    public TempOrderResponse(TemporaryOrderRedis entity) {
        this.memberId = entity.getMemberId();
        this.ordererName = entity.getOrdererName();
        this.phoneNumber = entity.getPhoneNumber();

        // null 방지 처리
        this.productNames = entity.getProductNames() != null ? entity.getProductNames() : new ArrayList<>();
        this.productSizes = entity.getProductSizes() != null ? entity.getProductSizes() : new ArrayList<>();
        this.productImages = entity.getProductImages() != null ? entity.getProductImages() : new ArrayList<>();
        this.productQuantities = entity.getProductQuantities() != null ? entity.getProductQuantities() : new ArrayList<>();

        this.totalPrice = entity.getTotalPrice() != null ? entity.getTotalPrice() : BigDecimal.ZERO;
    }
}
