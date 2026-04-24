package JOO.jooshop.contentImages.entity;

import JOO.jooshop.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "content_Images")
public class ContentImages {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_img_id")
    private Long contentImgId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "Images_path", nullable = false, length = 2000)
    private String ImagesPath;

    private ContentImages(String ImagesPath) {
        if (ImagesPath == null || ImagesPath.isBlank()) {
            throw new IllegalArgumentException("썸네일 경로는 비어 있을 수 없습니다ㅏ.");
        }
        this.ImagesPath = ImagesPath;
    }

    public static JOO.jooshop.contentImages.entity.ContentImages createContentImages(String ImagesPath) {
        return new JOO.jooshop.contentImages.entity.ContentImages(ImagesPath);
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
        return ImagesPath.startsWith("http://") || ImagesPath.startsWith("https://");
    }
}
