package JOO.jooshop.product.entity;

import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.contentImgs.entity.ContentImages;
import JOO.jooshop.contentImgs.entity.enums.UploadType;
import JOO.jooshop.global.time.BaseEntity;
import JOO.jooshop.product.entity.enums.Gender;
import JOO.jooshop.product.entity.enums.ProductType;
import JOO.jooshop.productManagement.entity.ProductManagement;
import JOO.jooshop.productManagement.entity.enums.Size;
import JOO.jooshop.thumbnail.entity.ProductThumbnail;
import JOO.jooshop.wishList.entity.WishList;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "products_table")
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType productType;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private BigDecimal price;

    private String productInfo;

    private String manufacturer;

    @Column(nullable = false)
    private boolean isDiscount = false;

    @Column(nullable = false)
    private int discountRate = 0;

    @Column(nullable = false)
    private boolean isRecommend = false;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ProductThumbnail> productThumbnails = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ContentImages> contentImages = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ProductManagement> productManagements = new ArrayList<>();

    @OneToMany(mappedBy = "product")
    private final List<WishList> wishLists = new ArrayList<>();

    private Long wishListCount;

    @Column(nullable = false)
    private boolean dummy = false;

    public static Product ofId(Long productId) {
        Product p = new Product();
        p.productId = productId;
        return p;
    }

    public static Product createDummy(
            String productName,
            ProductType type,
            BigDecimal price,
            String productInfo,
            String manufacturer,
            Boolean isDiscount,
            Integer discountRate,
            Boolean isRecommend
    ) {
        Product p = create(
                productName, type, price, productInfo, manufacturer,
                isDiscount, discountRate, isRecommend
        );
        p.dummy = true;
        return p;
    }

    public static Product create(
            String productName,
            ProductType type,
            BigDecimal price,
            String productInfo,
            String manufacturer,
            Boolean isDiscount,
            Integer discountRate,
            Boolean isRecommend
    ) {
        Product p = new Product();
        p.changeBasicInfo(
                productName,
                type,
                price,
                productInfo,
                manufacturer,
                isDiscount,
                discountRate,
                isRecommend
        );
        p.dummy = false;
        return p;
    }

    public void changeBasicInfo(
            String productName,
            ProductType type,
            BigDecimal price,
            String productInfo,
            String manufacturer,
            Boolean isDiscount,
            Integer discountRate,
            Boolean isRecommend
    ) {
        this.productName = requireText(productName, "productName");
        this.productType = requireNotNull(type, "productType");
        this.price = requireNotNull(price, "price");
        this.productInfo = productInfo;
        this.manufacturer = manufacturer;

        applyDiscount(Boolean.TRUE.equals(isDiscount), discountRate);
        this.isRecommend = Boolean.TRUE.equals(isRecommend);
    }

    public List<ProductThumbnail> thumbnailsView() {
        return Collections.unmodifiableList(productThumbnails);
    }

    public List<ContentImages> contentImagesView() {
        return Collections.unmodifiableList(contentImages);
    }

    public List<ProductManagement> optionsView() {
        return Collections.unmodifiableList(productManagements);
    }

    public void addThumbnailPath(String imagePath) {
        String path = requireText(imagePath, "imagePath");

        ProductThumbnail thumbnail = ProductThumbnail.createThumbnail(path);
        thumbnail.attachTo(this);
        this.productThumbnails.add(thumbnail);
    }

    public void clearThumbnails() {
        for (ProductThumbnail thumbnail : new ArrayList<>(this.productThumbnails)) {
            removeThumbnail(thumbnail);
        }
    }

    public void removeThumbnail(ProductThumbnail thumbnail) {
        if (thumbnail == null) return;

        if (this.productThumbnails.remove(thumbnail)) {
            thumbnail.detach();
        }
    }

    public void addContentImagePath(String imagePath, UploadType uploadType) {
        String path = requireText(imagePath, "imagePath");
        // UploadType type = requireNotNull(uploadType, "uploadType");

        ContentImages image = ContentImages.createContentImage(path);
        image.attachTo(this);
        this.contentImages.add(image);
    }

    public void clearContentImages() {
        for (ContentImages image : new ArrayList<>(this.contentImages)) {
            removeContentImage(image);
        }
    }

    public void removeContentImage(ContentImages image) {
        if (image == null) return;

        if (this.contentImages.remove(image)) {
            image.detach();
        }
    }

    public void addOption(
            ProductColor color,
            Category category,
            Gender gender,
            Size size,
            long stock
    ) {
        validateDuplicateOption(color, category, gender, size);

        ProductManagement option = ProductManagement.create(
                color, category, gender, size, stock
        );
        option.attachTo(this);
        this.productManagements.add(option);
    }

    public void addProductManagement(ProductManagement pm) {
        if (pm == null) {
            throw new IllegalArgumentException("productManagement must not be null");
        }

        validateDuplicateOption(pm.getColor(), pm.getCategory(), pm.getGender(), pm.getSize());
        pm.attachTo(this);
        this.productManagements.add(pm);
    }

    public void clearOptions() {
        for (ProductManagement pm : new ArrayList<>(this.productManagements)) {
            removeProductManagement(pm);
        }
    }

    public void replaceOptions(List<ProductManagement> newOptions) {
        clearOptions();

        if (newOptions == null || newOptions.isEmpty()) {
            return;
        }

        for (ProductManagement pm : newOptions) {
            addProductManagement(pm);
        }
    }

    public void removeProductManagement(ProductManagement pm) {
        if (pm == null) return;

        if (this.productManagements.remove(pm)) {
            pm.detach();
        }
    }

    private void validateDuplicateOption(
            ProductColor color,
            Category category,
            Gender gender,
            Size size
    ) {
        boolean duplicated = this.productManagements.stream()
                .anyMatch(pm -> pm.sameOption(color, category, gender, size));

        if (duplicated) {
            throw new IllegalStateException("already exists same option in product");
        }
    }

    public boolean isDummy() {
        return dummy;
    }

    public void markAsReal() {
        this.dummy = false;
    }

    private void applyDiscount(boolean discount, Integer rate) {
        this.isDiscount = discount;

        if (!discount) {
            this.discountRate = 0;
            return;
        }

        int normalizedRate = (rate == null ? 0 : rate);
        if (normalizedRate < 0 || normalizedRate > 100) {
            throw new IllegalArgumentException("discountRate must be between 0 and 100");
        }

        this.discountRate = normalizedRate;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}