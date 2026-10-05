package JOO.jooshop.wishList.service;

import JOO.jooshop.wishList.model.WishListResponseDto;
import JOO.jooshop.wishList.repository.WishListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WishListService {

    private final WishListRepository wishListRepository;

    /** 내 좋아요(위시리스트) 목록 — 최신순 */
    public List<WishListResponseDto> getMyWishList(Long memberId) {
        return wishListRepository.findAllByMemberId(memberId).stream()
                .map(WishListResponseDto::new)
                .toList();
    }
}
