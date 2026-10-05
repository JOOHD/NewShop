package JOO.jooshop.profiile.repository;

import JOO.jooshop.profiile.entity.Profiles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProfileRepository extends JpaRepository<Profiles, Long> {

    // 회원 ID로 프로필 1건 조회 (없으면 Optional.empty)
    Optional<Profiles> findByMemberId(Long memberId);
}
