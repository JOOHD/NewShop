package JOO.jooshop.wishList.repository;

import JOO.jooshop.wishList.entity.WishList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WishListRepository extends JpaRepository<WishList, Long> {

    @Query("select w from WishList w join fetch w.product where w.member.id = :memberId order by w.wishListId desc")
    List<WishList> findAllByMemberId(@Param("memberId") Long memberId);

    // 회원이 해당 상품을 이미 좋아요 했는지 여부
    boolean existsByMember_IdAndProduct_ProductId(Long memberId, Long productId);

    // 회원의 특정 상품 좋아요 삭제
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from WishList w where w.member.id = :memberId and w.product.productId = :productId")
    void deleteByMemberIdAndProductId(@Param("memberId") Long memberId, @Param("productId") Long productId);
}
