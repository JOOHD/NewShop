package JOO.jooshop.profiile.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QProfiles is a Querydsl query type for Profiles
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QProfiles extends EntityPathBase<Profiles> {

    private static final long serialVersionUID = -1110525077L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QProfiles profiles = new QProfiles("profiles");

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath introduction = createString("introduction");

    public final JOO.jooshop.members.entity.QMember member;

    public final EnumPath<JOO.jooshop.profiile.entity.enums.MemberAges> memberAges = createEnum("memberAges", JOO.jooshop.profiile.entity.enums.MemberAges.class);

    public final EnumPath<JOO.jooshop.profiile.entity.enums.MemberGender> memberGender = createEnum("memberGender", JOO.jooshop.profiile.entity.enums.MemberGender.class);

    public final NumberPath<Long> profileId = createNumber("profileId", Long.class);

    public final StringPath profileImgName = createString("profileImgName");

    public final StringPath profileImgPath = createString("profileImgPath");

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public QProfiles(String variable) {
        this(Profiles.class, forVariable(variable), INITS);
    }

    public QProfiles(Path<? extends Profiles> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QProfiles(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QProfiles(PathMetadata metadata, PathInits inits) {
        this(Profiles.class, metadata, inits);
    }

    public QProfiles(Class<? extends Profiles> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.member = inits.isInitialized("member") ? new JOO.jooshop.members.entity.QMember(forProperty("member"), inits.get("member")) : null;
    }

}

