package JOO.jooshop.Inquiry.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QInquiryReply is a Querydsl query type for InquiryReply
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QInquiryReply extends EntityPathBase<InquiryReply> {

    private static final long serialVersionUID = 1654069109L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QInquiryReply inquiryReply = new QInquiryReply("inquiryReply");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final QInquiry inquiry;

    public final NumberPath<Long> inquiryReplyId = createNumber("inquiryReplyId", Long.class);

    public final JOO.jooshop.members.entity.QMember replyBy;

    public final StringPath replyContent = createString("replyContent");

    public final StringPath replyTitle = createString("replyTitle");

    public QInquiryReply(String variable) {
        this(InquiryReply.class, forVariable(variable), INITS);
    }

    public QInquiryReply(Path<? extends InquiryReply> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QInquiryReply(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QInquiryReply(PathMetadata metadata, PathInits inits) {
        this(InquiryReply.class, metadata, inits);
    }

    public QInquiryReply(Class<? extends InquiryReply> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.inquiry = inits.isInitialized("inquiry") ? new QInquiry(forProperty("inquiry"), inits.get("inquiry")) : null;
        this.replyBy = inits.isInitialized("replyBy") ? new JOO.jooshop.members.entity.QMember(forProperty("replyBy"), inits.get("replyBy")) : null;
    }

}

