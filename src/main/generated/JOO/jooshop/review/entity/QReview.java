package JOO.jooshop.review.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QReview is a Querydsl query type for Review
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QReview extends EntityPathBase<Review> {

    private static final long serialVersionUID = -1626209537L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QReview review = new QReview("review");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final JOO.jooshop.payment.entity.QPaymentHistory paymentHistory;

    public final NumberPath<Integer> rating = createNumber("rating", Integer.class);

    public final ListPath<ReviewReply, QReviewReply> replies = this.<ReviewReply, QReviewReply>createList("replies", ReviewReply.class, QReviewReply.class, PathInits.DIRECT2);

    public final StringPath reviewContent = createString("reviewContent");

    public final NumberPath<Long> reviewId = createNumber("reviewId", Long.class);

    public final ListPath<JOO.jooshop.reviewImg.entity.ReviewImg, JOO.jooshop.reviewImg.entity.QReviewImg> reviewImages = this.<JOO.jooshop.reviewImg.entity.ReviewImg, JOO.jooshop.reviewImg.entity.QReviewImg>createList("reviewImages", JOO.jooshop.reviewImg.entity.ReviewImg.class, JOO.jooshop.reviewImg.entity.QReviewImg.class, PathInits.DIRECT2);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public QReview(String variable) {
        this(Review.class, forVariable(variable), INITS);
    }

    public QReview(Path<? extends Review> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QReview(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QReview(PathMetadata metadata, PathInits inits) {
        this(Review.class, metadata, inits);
    }

    public QReview(Class<? extends Review> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.paymentHistory = inits.isInitialized("paymentHistory") ? new JOO.jooshop.payment.entity.QPaymentHistory(forProperty("paymentHistory"), inits.get("paymentHistory")) : null;
    }

}

