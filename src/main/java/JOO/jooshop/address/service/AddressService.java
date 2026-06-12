package JOO.jooshop.address.service;

import JOO.jooshop.address.entity.Addresses;
import JOO.jooshop.address.model.AddressesReqeustDto;
import JOO.jooshop.address.repository.AddressRepository;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static JOO.jooshop.global.exception.ResponseMessageConstants.*;

@Service
@RequiredArgsConstructor
@Transactional
public class AddressService {

    private final AddressRepository addressRepository;
    private final MemberAccountService memberAccountService;

    /* 회원의 새로운 주소를 생성한다. */
    public ResponseEntity<Addresses> createAddress(Long memberId, AddressesReqeustDto addressDto) {
        Member member = memberAccountService.findMemberById(memberId);
        Addresses addresses = Addresses.createAddress(addressDto, member);

        if (addresses.isDefaultAddress()) {
            resetDefaultAddress(memberId);
        }

        addressRepository.save(addresses);
        return ResponseEntity.status(HttpStatus.CREATED).body(addresses);
    }

    /* 회원의 전체 주소 리스트 조회 */
    @Transactional(readOnly = true)
    public ResponseEntity<List<Addresses>> fetchAddressList(Long memberId) {
        Member member = memberAccountService.findMemberById(memberId);
        List<Addresses> memberAddressList = addressRepository.findAllByMember(member)
                .orElseThrow(() -> new NoSuchElementException(ADDRESS_NOT_FOUND));
        return ResponseEntity.status(HttpStatus.OK).body(memberAddressList);
    }

    /* 회원의 기본 주소 조회 */
    @Transactional(readOnly = true)
    public ResponseEntity<?> fetchDefaultAddress(Long memberId) {
        memberAccountService.findMemberById(memberId);
        Optional<Addresses> defaultAddress = addressRepository.findByMemberIdAndIsDefaultAddressIsTrue(memberId);

        if (defaultAddress.isPresent()) {
            return ResponseEntity.ok(defaultAddress.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ADDRESS_NOT_FOUND);
        }
    }

    /* 기본 주소 설정 */
    public ResponseEntity<?> setDefaultAddress(Long memberId, Long addressId) {
        Addresses address = findAddress(addressId);
        validateAddressOwner(memberId, address);

        resetDefaultAddress(memberId);
        address.setDefaultAddress(true);
        addressRepository.save(address);

        return ResponseEntity.status(HttpStatus.OK).body(address);
    }

    /** =================== 공통 메서드 =================== */
    private Addresses findAddress(Long addressId) {
        return addressRepository.findByAddressId(addressId)
                .orElseThrow(() -> new NoSuchElementException(ADDRESS_NOT_FOUND));
    }

    private void validateAddressOwner(Long memberId, Addresses address) {
        if (!address.getMember().getId().equals(memberId)) {
            throw new SecurityException(ACCESS_DENIED);
        }
    }

    private void resetDefaultAddress(Long memberId) {
        addressRepository.resetDefaultAddressForMember(memberId);
    }
}
