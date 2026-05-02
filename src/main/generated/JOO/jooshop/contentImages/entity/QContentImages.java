package JOO.jooshop.contentImages.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QContentImages is a Querydsl query type for ContentImages
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QContentImages extends EntityPathBase<ContentImages> {

    private static final long serialVersionUID = 1336797845L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QContentImages contentImages = new QContentImages("contentImages");

    public final NumberPath<Long> contentImgId = createNumber("contentImgId", Long.class);

    public final StringPath ImagesPath = createString("ImagesPath");

    public final JOO.jooshop.product.entity.QProduct product;

    public QContentImages(String variable) {
        this(ContentImages.class, forVariable(variable), INITS);
    }

    public QContentImages(Path<? extends ContentImages> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QContentImages(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QContentImages(PathMetadata metadata, PathInits inits) {
        this(ContentImages.class, metadata, inits);
    }

    public QContentImages(Class<? extends ContentImages> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.product = inits.isInitialized("product") ? new JOO.jooshop.product.entity.QProduct(forProperty("product")) : null;
    }

}

