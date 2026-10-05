package JOO.jooshop.product.model;

import JOO.jooshop.product.entity.ProductColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(of = "colorId")
@AllArgsConstructor
@NoArgsConstructor
public class ProductColorDto {
    private Long colorId;
    @NotBlank(message = "색상 이름은 필수입니다.")
    private String color;

    // 색상 엔티티를 DTO로 변환
    public ProductColorDto(ProductColor color) {
        this(
                color.getColorId(),
                color.getColor()
        );
    }
}
