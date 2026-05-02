package JOO.jooshop.order.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QOrderProduct is a Querydsl query type for OrderProduct
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QOrderProduct extends EntityPathBase<OrderProduct> {

    private static final long serialVersionUID = 1775631226L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QOrderProduct orderProduct = new QOrderProduct("orderProduct");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final QOrders orders;

    public final NumberPath<java.math.BigDecimal> priceAtOrder = createNumber("priceAtOrder", java.math.BigDecimal.class);

    public final StringPath productImg = createString("productImg");

    public final JOO.jooshop.productManagement.entity.QProductManagement productManagement;

    public final StringPath productName = createString("productName");

    public final StringPath productSize = createString("productSize");

    public final NumberPath<Integer> quantity = createNumber("quantity", Integer.class);

    public final BooleanPath returned = createBoolean("returned");

    public final BooleanPath reviewed = createBoolean("reviewed");

    public QOrderProduct(String variable) {
        this(OrderProduct.class, forVariable(variable), INITS);
    }

    public QOrderProduct(Path<? extends OrderProduct> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QOrderProduct(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QOrderProduct(PathMetadata metadata, PathInits inits) {
        this(OrderProduct.class, metadata, inits);
    }

    public QOrderProduct(Class<? extends OrderProduct> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.orders = inits.isInitialized("orders") ? new QOrders(forProperty("orders"), inits.get("orders")) : null;
        this.productManagement = inits.isInitialized("productManagement") ? new JOO.jooshop.productManagement.entity.QProductManagement(forProperty("productManagement"), inits.get("productManagement")) : null;
    }

}

