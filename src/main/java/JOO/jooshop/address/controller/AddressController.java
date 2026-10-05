package JOO.jooshop.address.controller;

import JOO.jooshop.address.entity.Addresses;
import JOO.jooshop.address.model.AddressesReqeustDto;
import JOO.jooshop.address.service.AddressService;
import JOO.jooshop.global.authorization.MemberAuthorizationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class AddressController {

    private final AddressService addressService;

    /* 회원 주소 목록 조회 */
    // 회원의 배송지 목록 조회
    @GetMapping("/address/{memberId}")
    public ResponseEntity<List<Addresses>> fetchAddressList(@PathVariable Long memberId) {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        return addressService.fetchAddressList(memberId);
    }

    /*
     * daum 주소 조회 (직접 입력)
     * 프론트에서 Daum 주소를 가져오든, 수기로 입력하든 상관없이
     * 서버는 “저장 + 기본 주소 처리 + 검증” 역할이 필요
     */
    @PostMapping("/address/{memberId}")
    public ResponseEntity<Addresses> createAddress(@PathVariable Long memberId,
                                                   @RequestBody AddressesReqeustDto addressDto) {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        return addressService.createAddress(memberId, addressDto);
    }

    /* 기본 주소 조회 */
    // 회원의 기본 배송지 조회
    @GetMapping("/address/default/{memberId}")
    public ResponseEntity<?> fetchDefaultAddress(@PathVariable Long memberId) {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        return addressService.fetchDefaultAddress(memberId);
    }

    /* 기본 주소 설정 */
    // 기본 배송지 변경
    @PutMapping("/address/default/{memberId}/{addressId}")
    public ResponseEntity<?> updateIsDefaultAddress(@PathVariable Long memberId,
                                                    @PathVariable Long addressId) {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        return addressService.setDefaultAddress(memberId, addressId);
    }
}
