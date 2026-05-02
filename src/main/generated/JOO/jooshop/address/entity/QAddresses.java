package JOO.jooshop.address.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QAddresses is a Querydsl query type for Addresses
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QAddresses extends EntityPathBase<Addresses> {

    private static final long serialVersionUID = -982887389L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QAddresses addresses = new QAddresses("addresses");

    public final StringPath address = createString("address");

    public final NumberPath<Long> addressId = createNumber("addressId", Long.class);

    public final StringPath addressName = createString("addressName");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath detailAddress = createString("detailAddress");

    public final BooleanPath isDefaultAddress = createBoolean("isDefaultAddress");

    public final JOO.jooshop.members.entity.QMember member;

    public final StringPath postCode = createString("postCode");

    public final StringPath recipient = createString("recipient");

    public final StringPath recipientPhone = createString("recipientPhone");

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public QAddresses(String variable) {
        this(Addresses.class, forVariable(variable), INITS);
    }

    public QAddresses(Path<? extends Addresses> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QAddresses(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QAddresses(PathMetadata metadata, PathInits inits) {
        this(Addresses.class, metadata, inits);
    }

    public QAddresses(Class<? extends Addresses> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.member = inits.isInitialized("member") ? new JOO.jooshop.members.entity.QMember(forProperty("member"), inits.get("member")) : null;
    }

}

