package JOO.jooshop.contentImgs.entity;

import JOO.jooshop.contentImgs.entity.enums.UploadType;
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
@Table(name = "content_imgs")
public class ContentImages {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_img_id")
    private Long contentImgId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "image_path", nullable = false, length = 2000)
    private String imagePath;

    private ContentImages(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            throw new IllegalArgumentException("썸네일 경로는 비어 있을 수 없습니다ㅏ.");
        }
        this.imagePath = imagePath;
    }

    public static ContentImages createContentImage(String imagePath) {
        return new ContentImages(imagePath);
    }

    public void attachTo(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product는 null일 수 없습니다.");
        }
        this.product = product;
    }

    public void detach() {
        this.product = null;
    }

    public boolean isExternalUrl() {
        return imagePath.startsWith("http://") || imagePath.startsWith("https://");
    }
}
