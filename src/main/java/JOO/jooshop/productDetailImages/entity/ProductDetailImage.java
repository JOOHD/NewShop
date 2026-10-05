package JOO.jooshop.productDetailImages.entity;

import JOO.jooshop.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "content_Images")
public class ProductDetailImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_img_id")
    private Long contentImgId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "Images_path", nullable = false, length = 2000)
    private String ImagesPath;

    // 상세 이미지 생성자 — 경로는 필수
    private ProductDetailImage(String ImagesPath) {
        if (ImagesPath == null || ImagesPath.isBlank()) {
            throw new IllegalArgumentException("썸네일 경로는 비어 있을 수 없습니다ㅏ.");
        }
        this.ImagesPath = ImagesPath;
    }

    // 상세 이미지 생성
    public static JOO.jooshop.productDetailImages.entity.ProductDetailImage createProductDetailImage(String ImagesPath) {
        return new JOO.jooshop.productDetailImages.entity.ProductDetailImage(ImagesPath);
    }

    // 상품과 연관관계 설정
    public void attachTo(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product는 null일 수 없습니다.");
        }
        this.product = product;
    }

    // 상품과의 연관관계 해제
    public void detach() {
        this.product = null;
    }

    // 외부 URL(http/https) 이미지인지 여부
    public boolean isExternalUrl() {
        return ImagesPath.startsWith("http://") || ImagesPath.startsWith("https://");
    }
}
