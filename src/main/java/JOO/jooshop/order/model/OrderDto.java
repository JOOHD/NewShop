package JOO.jooshop.order.model;

import JOO.jooshop.order.entity.enums.PayMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 주문 생성/확정 요청 DTO.
 * 요청 데이터 전달 전용 — setter 불필요.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderDto {

    private Long memberId;
    /** 장바구니 주문: 장바구니 항목 ID 목록 */
    private List<Long> cartIds;

    /** 바로 구매: 옵션과 수량 목록 (이 값이 있으면 장바구니를 거치지 않는다) */
    @Valid
    @Size(max = 20, message = "한 번에 주문할 수 있는 상품 종류는 최대 20개입니다.")
    private List<OrderItemRequest> items;

    @NotNull(message = "우편번호는 필수입니다.")
    private String postCode;

    @NotNull(message = "주소는 필수입니다.")
    private String address;

    private String detailAddress;

    @NotNull(message = "이름은 필수입니다.")
    private String username;

    private String ordererName;

    private String phoneNumber;

    private PayMethod payMethod;

    private String merchantUid;
}
