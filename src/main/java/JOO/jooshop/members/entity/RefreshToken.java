package JOO.jooshop.members.entity;


import JOO.jooshop.members.model.request.RefreshRequest;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "refresh")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_id")
    private Long refreshId;

    @Column(name = "refresh_token")
    private String refreshToken;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "member_id")
    private Member member;

//    @Column(columnDefinition = "DATETIME", name = "REFRESH_EXPIRATION") // H2Database의 경우
    @Column(columnDefinition = "TIMESTAMP") // MySQL의 경우
    private LocalDateTime expiration;

    // 리프레시 토큰 생성자 — 회원, 토큰 값, 만료 시각
    public RefreshToken(Member member, String refreshToken, LocalDateTime expiration) {
        this.member = member;
        this.refreshToken = refreshToken;
        this.expiration = expiration;
    }

    // 리프레시 토큰 재발급 시 토큰 값과 만료 시각 갱신
    public void updateRefreshToken(RefreshRequest refreshRequest) {
        this.refreshToken = refreshRequest.getRefreshToken(); // this.~ -> entity 필드
        this.expiration = refreshRequest.getExpirationDate();
    }
}
