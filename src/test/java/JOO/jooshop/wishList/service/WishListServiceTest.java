package JOO.jooshop.wishList.service;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.repository.ProductRepository;
import JOO.jooshop.wishList.entity.WishList;
import JOO.jooshop.wishList.repository.WishListRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * WishListService 단위 테스트 (Repository/타 서비스는 Mockito로 대체).
 *
 * 검증 포인트:
 * 1. 처음 누르면 저장된다
 * 2. 이미 좋아요한 상품이면 저장하지 않는다 (여러 번 눌러도 안전 — 멱등)
 * 3. 없는 상품이면 예외
 * 4. 삭제는 Repository로 위임, 목록이 없으면 빈 리스트
 */
@ExtendWith(MockitoExtension.class)
class WishListServiceTest {

    @Mock WishListRepository wishListRepository;
    @Mock MemberAccountService memberAccountService;
    @Mock ProductRepository productRepository;

    @InjectMocks WishListService wishListService;

    @Test
    @DisplayName("처음 좋아요하면 WishList가 저장된다")
    void add_firstTime_saves() {
        Member member = Member.registerGeneral("test@example.com", "encodedPw", "홍길동", "길동이", "010-1234-5678", "uuid");
        Product product = mock(Product.class);

        given(wishListRepository.existsByMember_IdAndProduct_ProductId(1L, 10L)).willReturn(false);
        given(memberAccountService.findMemberById(1L)).willReturn(member);
        given(productRepository.findById(10L)).willReturn(Optional.of(product));

        wishListService.add(1L, 10L);

        verify(wishListRepository, times(1)).save(any(WishList.class));
    }

    @Test
    @DisplayName("이미 좋아요한 상품이면 저장하지 않는다")
    void add_alreadyLiked_doesNothing() {
        given(wishListRepository.existsByMember_IdAndProduct_ProductId(1L, 10L)).willReturn(true);

        wishListService.add(1L, 10L);

        verify(wishListRepository, never()).save(any(WishList.class));
        verifyNoInteractions(memberAccountService, productRepository);
    }

    @Test
    @DisplayName("없는 상품이면 NoSuchElementException이 발생하고 저장되지 않는다")
    void add_productNotFound_throws() {
        Member member = Member.registerGeneral("test@example.com", "encodedPw", "홍길동", "길동이", "010-1234-5678", "uuid");

        given(wishListRepository.existsByMember_IdAndProduct_ProductId(1L, 999L)).willReturn(false);
        given(memberAccountService.findMemberById(1L)).willReturn(member);
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> wishListService.add(1L, 999L))
                .isInstanceOf(NoSuchElementException.class);

        verify(wishListRepository, never()).save(any(WishList.class));
    }

    @Test
    @DisplayName("삭제는 Repository로 위임하고, 좋아요가 없으면 빈 목록을 반환한다")
    void remove_delegates_and_emptyList() {
        wishListService.remove(1L, 10L);
        verify(wishListRepository).deleteByMemberIdAndProductId(1L, 10L);

        given(wishListRepository.findAllByMemberId(1L)).willReturn(List.of());
        assertThat(wishListService.getMyWishList(1L)).isEmpty();
    }
}
