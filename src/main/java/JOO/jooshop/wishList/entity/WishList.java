package JOO.jooshop.wishList.entity;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.product.entity.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(name = "wish_list")
public class WishList {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wishlist_id")
    private Long wishListId;

    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // 위시리스트 생성자 — 회원이 좋아요한 상품
    public WishList(Member member, Product product) {
        this.member = member;
        this.product = product;
    }

    // JPA용 기본 생성자
    public WishList() {

    }

}
