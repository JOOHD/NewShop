package JOO.jooshop.global.mail.entity;

import JOO.jooshop.members.entity.Member;
import com.siot.IamportRestClient.response.Certification;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "certification_entity")
public class CertificationEntity {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    // 이메일 인증 토큰 생성 — 생성 시각 기준 10분간 유효
    public static CertificationEntity create(String email, String token) {
        LocalDateTime now = LocalDateTime.now();
        return CertificationEntity.builder()
                .email(email)
                .token(token)
                .createdAt(now)
                .expiredAt(now.plusMinutes(10)) // 10분 유효
                .build();
    }

    public boolean isExpired() { // 인증 토큰이 만료됐는지를 판별
        return LocalDateTime.now().isAfter(this.expiredAt); // .isAfter() : now가 expiredAt보다 "뒤인가?", 즉 **지났는가?**를 의미
    }
}