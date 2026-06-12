package JOO.jooshop.cart.service;

import JOO.jooshop.cart.entity.Cart;
import JOO.jooshop.cart.model.CartDto;
import JOO.jooshop.cart.model.CartUpdateDto;
import JOO.jooshop.cart.repository.CartRepository;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.productManagement.entity.ProductManagement;
import JOO.jooshop.productManagement.repository.ProductManagementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

import static JOO.jooshop.global.authorization.MemberAuthorizationUtil.verifyUserIdMatch;
import static JOO.jooshop.global.exception.ResponseMessageConstants.PRODUCT_NOT_FOUND;

@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class CartService {

    /*
     * [Service]

     *기존
     * - 상품 추가 시 단순 insert 중심
     * - 동일 상품 옵션 중복 처리 로직이 분산되거나 약함
     * - 장바구니를 도메인 개념이 아닌 저장 데이터로 취급
     *
     * refactoring 26.04
     * - addCart(): 기존 상품 존재 시 수량 변경, 없으면 생성
     * - 장바구니 핵심 흐름을 서비스에서 orchestration
     * - 엔티티 도메인 메서드 호출 중심으로 상태 변경
     */

    private final CartRepository cartRepository;
    private final MemberAccountService memberAccountService;
    private final ProductManagementRepository productManagementRepository;

    /**
     * 로그인 사용자의 장바구니 전체 조회
     */
    @Transactional(readOnly = true)
    public List<CartDto> allCarts(Long memberId) {
        verifyUser(memberId);

        return cartRepository.findByMemberId(memberId).stream()
                .map(CartDto::toDto)
                .toList();
    }

    /**
     * 장바구니 담기 (기존 존재 시 수량 덮어쓰기, 없으면 신규 생성)
     */
    public Long addCart(Long memberId, Long inventoryId, int quantity) {
        verifyUser(memberId);

        Member member = findMember(memberId);
        ProductManagement productManagement = findProductManagement(inventoryId);

        Cart existingCart = cartRepository
                .findByMemberAndProductManagement(member, productManagement)
                .orElse(null);

        if (existingCart != null) {
            existingCart.replaceQuantity(quantity); // 기존 cart 수량 변경
            return existingCart.getCartId();
        }

        Cart cart = Cart.createCart(member, productManagement, quantity); // 신규 생성
        cartRepository.save(cart);
        return cart.getCartId();
    }

    /**
     * 장바구니 수량 수정 (소유자 검증 후 변경)
     */
    public CartDto updateCart(Long memberId, Long cartId, CartUpdateDto dto) {
        verifyUser(memberId);

        Cart cart = findOwnedCart(cartId, memberId);
        cart.changeQuantity(dto.getQuantity());

        return CartDto.toDto(cart);
    }

    /**
     * 장바구니 단일 삭제 (소유자 검증 포함)
     */
    public void deleteCart(Long cartId, Long memberId) {
        verifyUser(memberId);

        Cart cart = findOwnedCart(cartId, memberId);
        cartRepository.delete(cart);
    }

    /**
     * 장바구니 여러 건 삭제 (각 항목별 소유자 검증)
     */
    public void deleteCartList(List<Long> cartIds, Long memberId) {
        verifyUser(memberId);

        for (Long cartId : cartIds) {
            Cart cart = findOwnedCart(cartId, memberId);
            cartRepository.delete(cart);
        }
    }

    /**
     * 회원 조회 (없으면 예외)
     */
    private Member findMember(Long memberId) {
        return memberAccountService.findMemberById(memberId);
    }

    /**
     * 상품 옵션 조회 (없으면 예외)
     */
    private ProductManagement findProductManagement(Long inventoryId) {
        return productManagementRepository.findById(inventoryId)
                .orElseThrow(() -> new NoSuchElementException(PRODUCT_NOT_FOUND + " inventoryId: " + inventoryId));
    }

    /**
     * 장바구니 조회 (없으면 예외)
     */
    private Cart findCartById(Long cartId) {
        return cartRepository.findById(cartId)
                .orElseThrow(() -> new NoSuchElementException("장바구니를 찾을 수 없습니다. cartId: " + cartId));
    }

    /**
     * 장바구니 소유자 검증 후 반환
     */
    private Cart findOwnedCart(Long cartId, Long memberId) {
        Cart cart = findCartById(cartId);

        if (!cart.isOwnedBy(memberId)) {
            throw new SecurityException("해당 장바구니에 대한 권한이 없습니다.");
        }
        return cart;
    }

    /**
     * 인증 사용자 유효성 검증
     */
    private void verifyUser(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId가 null 입니다.");
        }
        verifyUserIdMatch(memberId);
    }
}