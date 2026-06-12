package JOO.jooshop.members.repository;

import JOO.jooshop.members.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 이메일 중복 가입 방지용
    boolean existsByEmail(String email);

    // 일반 로그인 / 이메일 인증 조회용
    Optional<Member> findByEmail(String email);

    // OAuth2 로그인 조회용 (provider_providerId 형태의 복합 식별자)
    Optional<Member> findBySocialId(String socialId);

    void deleteByEmail(String email);

    // joined_at 이 null 인 기존 데이터 일괄 보정용 배치 쿼리
    // clearAutomatically: 쿼리 실행 후 영속성 컨텍스트 초기화 → DB 상태와 동기화
    // flushAutomatically: 실행 전 미반영 변경사항 먼저 flush → 데이터 정합성 보장
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = "UPDATE member SET joined_at = CURRENT_TIMESTAMP WHERE joined_at IS NULL", nativeQuery = true)
    int fillNullJoinedAt();
}