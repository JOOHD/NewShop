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

    private ProductThumbnail(String ImagesPath) {
        if (ImagesPath == null || ImagesPath.isBlank()) {
            throw new IllegalArgumentException("썸네일 경로는 비어 있을 수 없습니다ㅏ.");
        }
        this.ImagesPath = ImagesPath;
    }

    public static ProductThumbnail createThumbnail(String ImagesPath) {
        return new ProductThumbnail(ImagesPath);
    }

    public void attachTo(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product는 null일 수 없습니다.");
        }
        this.product = null;
    }

    public void detach() {
        this.product = null;
    }

    public boolean isExternalUrl() {
        return ImagesPath.startsWith("http://") || ImagesPath.startsWith("https://");
    }

}
