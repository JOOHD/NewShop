package JOO.jooshop.product.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QProductColor is a Querydsl query type for ProductColor
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QProductColor extends EntityPathBase<ProductColor> {

    private static final long serialVersionUID = 1046798606L;

    public static final QProductColor productColor = new QProductColor("productColor");

    public final StringPath color = createString("color");

    public final NumberPath<Long> colorId = createNumber("colorId", Long.class);

    public QProductColor(String variable) {
        super(ProductColor.class, forVariable(variable));
    }

    public QProductColor(Path<? extends ProductColor> path) {
        super(path.getType(), path.getMetadata());
    }

    public QProductColor(PathMetadata metadata) {
        super(ProductColor.class, metadata);
    }

}

