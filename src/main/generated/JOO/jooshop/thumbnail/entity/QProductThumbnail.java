package JOO.jooshop.thumbnail.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QProductThumbnail is a Querydsl query type for ProductThumbnail
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QProductThumbnail extends EntityPathBase<ProductThumbnail> {

    private static final long serialVersionUID = -1146968428L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QProductThumbnail productThumbnail = new QProductThumbnail("productThumbnail");

    public final StringPath imagePath = createString("imagePath");

    public final JOO.jooshop.product.entity.QProduct product;

    public final NumberPath<Long> thumbnailId = createNumber("thumbnailId", Long.class);

    public QProductThumbnail(String variable) {
        this(ProductThumbnail.class, forVariable(variable), INITS);
    }

    public QProductThumbnail(Path<? extends ProductThumbnail> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QProductThumbnail(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QProductThumbnail(PathMetadata metadata, PathInits inits) {
        this(ProductThumbnail.class, metadata, inits);
    }

    public QProductThumbnail(Class<? extends ProductThumbnail> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.product = inits.isInitialized("product") ? new JOO.jooshop.product.entity.QProduct(forProperty("product")) : null;
    }

}

