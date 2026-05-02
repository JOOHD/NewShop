package JOO.jooshop.Inquiry.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QInquiry is a Querydsl query type for Inquiry
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QInquiry extends EntityPathBase<Inquiry> {

    private static final long serialVersionUID = 209357941L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QInquiry inquiry = new QInquiry("inquiry");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath email = createString("email");

    public final StringPath inquiryContent = createString("inquiryContent");

    public final NumberPath<Long> inquiryId = createNumber("inquiryId", Long.class);

    public final StringPath inquiryTitle = createString("inquiryTitle");

    public final EnumPath<JOO.jooshop.Inquiry.entity.enums.InquiryType> inquiryType = createEnum("inquiryType", JOO.jooshop.Inquiry.entity.enums.InquiryType.class);

    public final BooleanPath isResponse = createBoolean("isResponse");

    public final BooleanPath isSecret = createBoolean("isSecret");

    public final JOO.jooshop.members.entity.QMember member;

    public final StringPath name = createString("name");

    public final StringPath password = createString("password");

    public final JOO.jooshop.product.entity.QProduct product;

    public final ListPath<InquiryReply, QInquiryReply> replies = this.<InquiryReply, QInquiryReply>createList("replies", InquiryReply.class, QInquiryReply.class, PathInits.DIRECT2);

    public QInquiry(String variable) {
        this(Inquiry.class, forVariable(variable), INITS);
    }

    public QInquiry(Path<? extends Inquiry> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QInquiry(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QInquiry(PathMetadata metadata, PathInits inits) {
        this(Inquiry.class, metadata, inits);
    }

    public QInquiry(Class<? extends Inquiry> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.member = inits.isInitialized("member") ? new JOO.jooshop.members.entity.QMember(forProperty("member"), inits.get("member")) : null;
        this.product = inits.isInitialized("product") ? new JOO.jooshop.product.entity.QProduct(forProperty("product")) : null;
    }

}

