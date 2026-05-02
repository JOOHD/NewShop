package JOO.jooshop.payment.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QPaymentRefund is a Querydsl query type for PaymentRefund
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QPaymentRefund extends EntityPathBase<PaymentRefund> {

    private static final long serialVersionUID = 406116941L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QPaymentRefund paymentRefund = new QPaymentRefund("paymentRefund");

    public final NumberPath<Integer> amount = createNumber("amount", Integer.class);

    public final NumberPath<Integer> checksum = createNumber("checksum", Integer.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath impUid = createString("impUid");

    public final QPaymentHistory paymentHistory;

    public final StringPath reason = createString("reason");

    public final StringPath refundAccount = createString("refundAccount");

    public final DateTimePath<java.time.LocalDateTime> refundAt = createDateTime("refundAt", java.time.LocalDateTime.class);

    public final StringPath refundBank = createString("refundBank");

    public final StringPath refundHolder = createString("refundHolder");

    public final StringPath refundTel = createString("refundTel");

    public QPaymentRefund(String variable) {
        this(PaymentRefund.class, forVariable(variable), INITS);
    }

    public QPaymentRefund(Path<? extends PaymentRefund> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QPaymentRefund(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QPaymentRefund(PathMetadata metadata, PathInits inits) {
        this(PaymentRefund.class, metadata, inits);
    }

    public QPaymentRefund(Class<? extends PaymentRefund> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.paymentHistory = inits.isInitialized("paymentHistory") ? new QPaymentHistory(forProperty("paymentHistory"), inits.get("paymentHistory")) : null;
    }

}

