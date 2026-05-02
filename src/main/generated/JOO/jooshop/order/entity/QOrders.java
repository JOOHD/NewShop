package JOO.jooshop.order.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QOrders is a Querydsl query type for Orders
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QOrders extends EntityPathBase<Orders> {

    private static final long serialVersionUID = 663008094L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QOrders orders = new QOrders("orders");

    public final StringPath address = createString("address");

    public final StringPath detailAddress = createString("detailAddress");

    public final JOO.jooshop.members.entity.QMember member;

    public final StringPath merchantUid = createString("merchantUid");

    public final DateTimePath<java.time.LocalDateTime> orderDay = createDateTime("orderDay", java.time.LocalDateTime.class);

    public final StringPath ordererName = createString("ordererName");

    public final NumberPath<Long> orderId = createNumber("orderId", Long.class);

    public final ListPath<OrderProduct, QOrderProduct> orderProducts = this.<OrderProduct, QOrderProduct>createList("orderProducts", OrderProduct.class, QOrderProduct.class, PathInits.DIRECT2);

    public final ListPath<JOO.jooshop.payment.entity.PaymentHistory, JOO.jooshop.payment.entity.QPaymentHistory> paymentHistories = this.<JOO.jooshop.payment.entity.PaymentHistory, JOO.jooshop.payment.entity.QPaymentHistory>createList("paymentHistories", JOO.jooshop.payment.entity.PaymentHistory.class, JOO.jooshop.payment.entity.QPaymentHistory.class, PathInits.DIRECT2);

    public final EnumPath<JOO.jooshop.payment.entity.PaymentStatus> paymentStatus = createEnum("paymentStatus", JOO.jooshop.payment.entity.PaymentStatus.class);

    public final EnumPath<JOO.jooshop.order.entity.enums.PayMethod> payMethod = createEnum("payMethod", JOO.jooshop.order.entity.enums.PayMethod.class);

    public final StringPath phoneNumber = createString("phoneNumber");

    public final StringPath postCode = createString("postCode");

    public final StringPath productNameSummary = createString("productNameSummary");

    public final NumberPath<java.math.BigDecimal> totalPrice = createNumber("totalPrice", java.math.BigDecimal.class);

    public QOrders(String variable) {
        this(Orders.class, forVariable(variable), INITS);
    }

    public QOrders(Path<? extends Orders> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QOrders(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QOrders(PathMetadata metadata, PathInits inits) {
        this(Orders.class, metadata, inits);
    }

    public QOrders(Class<? extends Orders> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.member = inits.isInitialized("member") ? new JOO.jooshop.members.entity.QMember(forProperty("member"), inits.get("member")) : null;
    }

}

