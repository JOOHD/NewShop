package JOO.jooshop.productManagement.entity;

import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.ProductColor;
import JOO.jooshop.product.entity.enums.Gender;
import JOO.jooshop.productManagement.entity.enums.Size;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "product_management",
        indexes = {
                @Index(name = "idx_pm_product", columnList = "product_id"),
                @Index(name = "idx_pm_category", columnList = "category_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_pm_option",
                        columnNames = {"product_id", "gender", "size", "color_id", "category_id"}
                )
        }
)
public class ProductManagement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_id")
    private Long inventoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "color_id", nullable = false)
    private ProductColor color;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "size", nullable = false, length = 20)
    private Size size;

    @Column(name = "initial_stock", nullable = false)
    private long initialStock;

    @Column(name = "additional_stock", nullable = false)
    private long additionalStock;

    @Column(name = "product_stock", nullable = false)
    private long productStock;

    @Column(name = "is_sold_out", nullable = false)
    private boolean soldOut;

    @Column(name = "is_restock_available", nullable = false)
    private boolean restockAvailable;

    @Column(name = "is_restocked", nullable = false)
    private boolean restocked;

    @ManyToMany(mappedBy = "productManagements")
    private final List<Orders> orders = new ArrayList<>();

    public static ProductManagement create(
            ProductColor color,
            Category category,
            Gender gender,
            Size size,
            long stock
    ) {
        validateRequired(color, category, gender, size);
        validateStock(stock);

        ProductManagement pm = new ProductManagement();
        pm.color = color;
        pm.category = category;
        pm.gender = gender;
        pm.size = size;

        pm.initialStock = stock;
        pm.additionalStock = 0L;
        pm.productStock = stock;
        pm.restockAvailable = false;
        pm.restocked = false;
        pm.soldOut = (stock == 0);

        return pm;
    }

    public static ProductManagement of(
            ProductColor color,
            Category category,
            Gender gender,
            Size size,
            long initialStock,
            Boolean restockAvailable,
            Boolean restocked,
            Boolean soldOut
    ) {
        validateRequired(color, category, gender, size);
        validateStock(initialStock);

        ProductManagement pm = new ProductManagement();
        pm.color = color;
        pm.category = category;
        pm.gender = gender;
        pm.size = size;

        pm.initialStock = initialStock;
        pm.additionalStock = 0L;
        pm.productStock = initialStock;
        pm.restockAvailable = Boolean.TRUE.equals(restockAvailable);
        pm.restocked = Boolean.TRUE.equals(restocked);
        pm.soldOut = Boolean.TRUE.equals(soldOut) || (initialStock == 0);

        return pm;
    }

    public void attachTo(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product must not be null");
        }
        this.product = product;
    }

    public void detach() {
        this.product = null;
    }

    public boolean sameOption(
            ProductColor color,
            Category category,
            Gender gender,
            Size size
    ) {
        return this.color.equals(color)
                && this.category.equals(category)
                && this.gender == gender
                && this.size == size;
    }

    public void changeCategory(Category category) {
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        this.category = category;
    }

    public void restock(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("restock amount must be positive");
        }

        this.additionalStock += amount;
        this.productStock += amount;
        this.restocked = true;
        this.soldOut = (this.productStock == 0);
    }

    public void decreaseStock(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("decrease amount must be positive");
        }
        if (this.productStock < amount) {
            throw new IllegalStateException("insufficient stock");
        }

        this.productStock -= amount;
        this.soldOut = (this.productStock == 0);
    }

    public void adjustStock(long newStock) {
        if (newStock < 0) {
            throw new IllegalArgumentException("stock must be >= 0");
        }

        this.productStock = newStock;
        this.soldOut = (this.productStock == 0);
    }

    public void setRestockAvailable(boolean available) {
        this.restockAvailable = available;
    }

    private static void validateRequired(
            ProductColor color,
            Category category,
            Gender gender,
            Size size
    ) {
        if (color == null) throw new IllegalArgumentException("color must not be null");
        if (category == null) throw new IllegalArgumentException("category must not be null");
        if (gender == null) throw new IllegalArgumentException("gender must not be null");
        if (size == null) throw new IllegalArgumentException("size must not be null");
    }

    private static void validateStock(long stock) {
        if (stock < 0) throw new IllegalArgumentException("stock must be >= 0");
    }
}