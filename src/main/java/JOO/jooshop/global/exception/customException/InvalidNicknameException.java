package JOO.jooshop.global.exception.customException;

public class InvalidNicknameException extends RuntimeException {

    // 예외 메시지를 지정해 생성
    public InvalidNicknameException(String message) {
        super(message);
    }
}
