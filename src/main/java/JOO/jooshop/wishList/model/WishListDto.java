package JOO.jooshop.wishList.model;

import JOO.jooshop.wishList.entity.WishList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WishListDto {
    private Long wishListId;
    private Long memberId;
    private Long productId;

    // 위시리스트 엔티티를 DTO로 변환
    public WishListDto(WishList wishList) {
        this(
                wishList.getWishListId(),
                wishList.getMember().getId(),
                wishList.getProduct().getProductId()
        );
    }

}
