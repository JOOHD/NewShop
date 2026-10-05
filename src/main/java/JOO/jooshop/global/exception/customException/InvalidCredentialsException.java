package JOO.jooshop.global.exception.customException;

public class InvalidCredentialsException extends RuntimeException {

    // 예외 메시지를 지정해 생성
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
