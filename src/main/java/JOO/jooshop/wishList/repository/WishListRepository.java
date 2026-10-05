package JOO.jooshop.wishList.repository;

import JOO.jooshop.wishList.entity.WishList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WishListRepository extends JpaRepository<WishList, Long> {

    @Query("select w from WishList w join fetch w.product where w.member.id = :memberId order by w.wishListId desc")
    List<WishList> findAllByMemberId(@Param("memberId") Long memberId);
}
