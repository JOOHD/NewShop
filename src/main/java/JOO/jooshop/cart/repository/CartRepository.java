package JOO.jooshop.cart.repository;

import JOO.jooshop.cart.entity.Cart;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.productVariant.entity.ProductVariant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * 회원 장바구니 전부 가져오기
     * EntityGraph: 연관 엔티티를 조회 시점에 함께 fetch 하도록 jpa에 힌트를 주는 것
     */
    @EntityGraph(attributePaths = {"productVariant", "productVariant.product"})
    List<Cart> findByMemberId(Long memberId);

    /**
     * 이 회원이 이 옵션 상품 이미 담았는지 찾아봐
     */
    Optional<Cart> findByMemberAndProductVariant(Member member, ProductVariant productVariant);

    /**
     * cartId 들 한 번에 찾아와
     */
    List<Cart> findByCartIdIn(List<Long> cartIds);

    /**
     * 이 cartId들 중에서 이 회원 것이 맞는 것만 가져와
     */
    @Query("""
        select c
        from Cart c
        where c.cartId in :cartIds
          and c.member.id = :memberId
    """)
    List<Cart> findOwnedCartsByIds(List<Long> cartIds, Long memberId);
}