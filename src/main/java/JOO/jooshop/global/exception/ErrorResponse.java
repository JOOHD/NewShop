package JOO.jooshop.global.exception;

import java.time.LocalDateTime;

public class ErrorResponse {
    private int status;              // 상태 코드 ex) 400, 401, 500..
    private String error;            // 상태 이름 ex) BAD_REQEUST
    private String message;          // 에러 메시지

    private LocalDateTime timestamp; // 에러 발생 시간

    // 상태 코드, 오류명, 메시지로 오류 응답 생성 (발생 시각 포함)
    public ErrorResponse(int status, String error, String message) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    // HTTP 상태 코드
    public int getStatus() {
        return status;
    }

    // HTTP 오류명
    public String getError() {
        return error;
    }

    // 오류 메시지
    public String getMessage() {
        return message;
    }

    // 오류 발생 시각
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
