package JOO.jooshop.productManagement.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QProductManagement is a Querydsl query type for ProductManagement
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QProductManagement extends EntityPathBase<ProductManagement> {

    private static final long serialVersionUID = 1721785013L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QProductManagement productManagement = new QProductManagement("productManagement");

    public final NumberPath<Long> additionalStock = createNumber("additionalStock", Long.class);

    public final JOO.jooshop.categorys.entity.QCategory category;

    public final JOO.jooshop.product.entity.QProductColor color;

    public final EnumPath<JOO.jooshop.product.entity.enums.Gender> gender = createEnum("gender", JOO.jooshop.product.entity.enums.Gender.class);

    public final NumberPath<Long> initialStock = createNumber("initialStock", Long.class);

    public final NumberPath<Long> inventoryId = createNumber("inventoryId", Long.class);

    public final ListPath<JOO.jooshop.order.entity.Orders, JOO.jooshop.order.entity.QOrders> orders = this.<JOO.jooshop.order.entity.Orders, JOO.jooshop.order.entity.QOrders>createList("orders", JOO.jooshop.order.entity.Orders.class, JOO.jooshop.order.entity.QOrders.class, PathInits.DIRECT2);

    public final JOO.jooshop.product.entity.QProduct product;

    public final NumberPath<Long> productStock = createNumber("productStock", Long.class);

    public final BooleanPath restockAvailable = createBoolean("restockAvailable");

    public final BooleanPath restocked = createBoolean("restocked");

    public final EnumPath<JOO.jooshop.productManagement.entity.enums.Size> size = createEnum("size", JOO.jooshop.productManagement.entity.enums.Size.class);

    public final BooleanPath soldOut = createBoolean("soldOut");

    public QProductManagement(String variable) {
        this(ProductManagement.class, forVariable(variable), INITS);
    }

    public QProductManagement(Path<? extends ProductManagement> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QProductManagement(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QProductManagement(PathMetadata metadata, PathInits inits) {
        this(ProductManagement.class, metadata, inits);
    }

    public QProductManagement(Class<? extends ProductManagement> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.category = inits.isInitialized("category") ? new JOO.jooshop.categorys.entity.QCategory(forProperty("category"), inits.get("category")) : null;
        this.color = inits.isInitialized("color") ? new JOO.jooshop.product.entity.QProductColor(forProperty("color")) : null;
        this.product = inits.isInitialized("product") ? new JOO.jooshop.product.entity.QProduct(forProperty("product")) : null;
    }

}

