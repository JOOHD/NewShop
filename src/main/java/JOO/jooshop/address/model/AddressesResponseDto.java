package JOO.jooshop.address.model;

import JOO.jooshop.address.entity.Addresses;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AddressesResponseDto {

    private Long addressId;       // PK
    private String addressName;   // 주소 이름 (집, 회사)
    private String recipient;     // 수령인
    private String postCode;      // 우편번호
    private String address;       // 기본 주소
    private String detailAddress; // 상세 주소
    private boolean defaultAddress;
    private String recipientPhone;

    /* DTO -> Entity */
    // 배송지 엔티티를 응답 DTO로 변환
    public static AddressesResponseDto toEntity(Addresses addresses) {
        return AddressesResponseDto.builder()
                .addressId(addresses.getAddressId())
                .addressName(addresses.getAddressName())
                .recipient(addresses.getRecipient())
                .postCode(addresses.getPostCode())
                .address(addresses.getAddress())
                .detailAddress(addresses.getDetailAddress())
                .defaultAddress(addresses.isDefaultAddress())
                .recipientPhone(addresses.getRecipientPhone())
                .build();
    }
}
