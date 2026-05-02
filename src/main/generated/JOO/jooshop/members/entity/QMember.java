package JOO.jooshop.members.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QMember is a Querydsl query type for Member
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QMember extends EntityPathBase<Member> {

    private static final long serialVersionUID = -132042210L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QMember member = new QMember("member1");

    public final JOO.jooshop.global.time.QBaseEntity _super = new JOO.jooshop.global.time.QBaseEntity(this);

    public final BooleanPath accountExpired = createBoolean("accountExpired");

    public final BooleanPath active = createBoolean("active");

    public final BooleanPath admin = createBoolean("admin");

    public final BooleanPath availableForLogin = createBoolean("availableForLogin");

    public final BooleanPath banned = createBoolean("banned");

    public final BooleanPath certifiedByEmail = createBoolean("certifiedByEmail");

    //inherited
    public final DateTimePath<java.time.LocalDateTime> createdAt = _super.createdAt;

    public final StringPath email = createString("email");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final DateTimePath<java.time.LocalDateTime> joinedAt = createDateTime("joinedAt", java.time.LocalDateTime.class);

    public final EnumPath<JOO.jooshop.members.entity.enums.MemberRole> memberRole = createEnum("memberRole", JOO.jooshop.members.entity.enums.MemberRole.class);

    public final StringPath nickname = createString("nickname");

    public final StringPath password = createString("password");

    public final BooleanPath passwordExpired = createBoolean("passwordExpired");

    public final StringPath phoneNumber = createString("phoneNumber");

    public final JOO.jooshop.profiile.entity.QProfiles profile;

    public final StringPath socialId = createString("socialId");

    public final EnumPath<JOO.jooshop.members.entity.enums.SocialType> socialType = createEnum("socialType", JOO.jooshop.members.entity.enums.SocialType.class);

    //inherited
    public final DateTimePath<java.time.LocalDateTime> updatedAt = _super.updatedAt;

    public final StringPath username = createString("username");

    public QMember(String variable) {
        this(Member.class, forVariable(variable), INITS);
    }

    public QMember(Path<? extends Member> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QMember(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QMember(PathMetadata metadata, PathInits inits) {
        this(Member.class, metadata, inits);
    }

    public QMember(Class<? extends Member> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.profile = inits.isInitialized("profile") ? new JOO.jooshop.profiile.entity.QProfiles(forProperty("profile"), inits.get("profile")) : null;
    }

}

