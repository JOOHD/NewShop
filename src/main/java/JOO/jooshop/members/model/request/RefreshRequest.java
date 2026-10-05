package JOO.jooshop.members.model.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RefreshRequest {

    private String refreshToken;

    private LocalDateTime expirationDate;

    // 리프레시 토큰과 만료 시각을 담는 요청 객체
    public RefreshRequest(String refreshToken, LocalDateTime expirationDate) {
        this.refreshToken = refreshToken;
        this.expirationDate = expirationDate;
    }

    // 새 리프레시 토큰/만료 시각으로 요청 객체 생성
    public static RefreshRequest createRefreshDto(String newRefreshToken, LocalDateTime expirationDateTime) {
        return new RefreshRequest(newRefreshToken, expirationDateTime);
    }
}
