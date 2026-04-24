package JOO.jooshop.thumbnail.model;

import JOO.jooshop.thumbnail.entity.ProductThumbnail;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;

@Data
@Getter
@EqualsAndHashCode(of = "thumbnailId")
@AllArgsConstructor
public class ProductThumbnailDto {

    private Long thumbnailId;   // 썸네일 고유 ID
    private String ImagesPath;   // DB에 저장된 상대 URL 또는 이미지 경로
    private Long productId;     // 연관된 Product의 ID

    /** Entity -> DTO 변환 생성자 */
    public ProductThumbnailDto(ProductThumbnail thumbnail) {
        this.thumbnailId = thumbnail.getThumbnailId();
        this.ImagesPath = thumbnail.getImagesPath();
        this.productId = thumbnail.getProduct().getProductId();
    }
}
