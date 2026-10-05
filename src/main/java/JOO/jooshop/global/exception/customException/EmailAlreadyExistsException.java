package JOO.jooshop.global.exception.customException;

public class EmailAlreadyExistsException extends RuntimeException {

    // 예외 메시지를 지정해 생성
    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
