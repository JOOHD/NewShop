package JOO.jooshop.thumbnail.entity;

import JOO.jooshop.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_thumbnails")
public class ProductThumbnail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "thumbnail_id")
    private Long thumbnailId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "Images_path", nullable = false, length = 2000)
    private String ImagesPath;

    // 썸네일 생성자 — 경로는 필수
    private ProductThumbnail(String ImagesPath) {
        if (ImagesPath == null || ImagesPath.isBlank()) {
            throw new IllegalArgumentException("썸네일 경로는 비어 있을 수 없습니다ㅏ.");
        }
        this.ImagesPath = ImagesPath;
    }

    // 썸네일 생성
    public static ProductThumbnail createThumbnail(String ImagesPath) {
        return new ProductThumbnail(ImagesPath);
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
