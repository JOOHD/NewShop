package JOO.jooshop.productVariant.model;

import JOO.jooshop.productVariant.entity.ProductVariant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class InventoryUpdateDto {
//    private Long colorId;
    private Long categoryId;
//    private Size size;
//    private Long initialStock; // 수정할 땐 초기 재고 수정 불가
    private Long additionalStock;
    private Long productStock;
    private Boolean isRestockAvailable = false;
    private Boolean isRestocked = false;
    private Boolean isSoldOut = false;

    public InventoryUpdateDto(ProductVariant productVariant) {
        this(

//                productVariant.getColor().getColorId(),
                productVariant.getCategory().getCategoryId(),
//                productVariant.getSize(),
                productVariant.getAdditionalStock(),
                productVariant.getProductStock(),
                productVariant.isRestockAvailable(),
                productVariant.isRestocked(),
                productVariant.isSoldOut()
        );
    }
/*

    public static ProductVariant updateInventoryForm(ProductVariant existingInventory, InventoryUpdateDto request) {
        // 색상 및 카테고리 설정
*/
/*        existingInventory.setColor(ProductColor.createProductColorById(request.getColorId()));
        existingInventory.setCategory(Category.createCategoryById(request.getCategoryId()));*//*


        // 사이즈, 추가 재고 설정
//        existingInventory.setSize(request.getSize());
        Long additionalStock = request.getAdditionalStock();
        existingInventory.setAdditionalStock(additionalStock);

        // 상품 재고 및 재고 관련 설정
        existingInventory.setProductStock(existingInventory.getInitialStock() + additionalStock);
        existingInventory.setRestockAvailable(request.getIsRestockAvailable());
        existingInventory.setRestocked(request.getIsRestocked());
        existingInventory.setSoldOut(request.getIsSoldOut());

        return existingInventory;
    }

*/


}
