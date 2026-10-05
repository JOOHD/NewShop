package JOO.jooshop.wishList.service;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.repository.ProductRepository;
import JOO.jooshop.wishList.entity.WishList;
import JOO.jooshop.wishList.model.WishListResponseDto;
import JOO.jooshop.wishList.repository.WishListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WishListService {

    private final WishListRepository wishListRepository;
    private final MemberAccountService memberAccountService;
    private final ProductRepository productRepository;

    // 내 좋아요(위시리스트) 목록 조회 — 최신순
    public List<WishListResponseDto> getMyWishList(Long memberId) {
        return wishListRepository.findAllByMemberId(memberId).stream()
                .map(WishListResponseDto::new)
                .toList();
    }

    // 좋아요 추가 — 이미 좋아요한 상품이면 아무것도 하지 않음 (여러 번 눌러도 안전)
    @Transactional
    public void add(Long memberId, Long productId) {
        if (wishListRepository.existsByMember_IdAndProduct_ProductId(memberId, productId)) {
            return;
        }

        Member member = memberAccountService.findMemberById(memberId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NoSuchElementException("해당 상품을 찾을 수 없습니다. Id : " + productId));

        wishListRepository.save(new WishList(member, product));
    }

    // 좋아요 삭제 — 좋아요하지 않은 상품이면 아무것도 하지 않음
    @Transactional
    public void remove(Long memberId, Long productId) {
        wishListRepository.deleteByMemberIdAndProductId(memberId, productId);
    }
}
