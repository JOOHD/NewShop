package JOO.jooshop.global.queries;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueryDSLConfig {
    /*
        QClass ?
        1. 컴파일 타임 오류 방지: Q클래스를 통해 필드에 타입 안전한 접근을 제공,
                   쿼리 작성 시 발생할 수 있는 실수를 미리 방지합니다.
        2. 동적 쿼리 지원: 동적인 조건에 따라 AND, OR 조건을 자유롭게 추가할 수 있어,
                   복잡한 쿼리를 쉽게 작성할 수 있습니다.
        3. 엔티티의 메타모델 역할: JPA 엔티티와의 1:1 대응으로, 엔티티 필드를 쿼리 조건에 쉽게 활용할 수 있습니다.
     */

    private final EntityManager entityManager;
    // EntityManager 주입
    public QueryDSLConfig(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // QueryDSL 쿼리 팩토리 빈 등록
    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}