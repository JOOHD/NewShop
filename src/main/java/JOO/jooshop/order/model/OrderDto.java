package JOO.jooshop.order.model;

import JOO.jooshop.order.entity.enums.PayMethod;
import jakarta.validation.constraints.NotNull;
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
    private List<Long> cartIds;

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
