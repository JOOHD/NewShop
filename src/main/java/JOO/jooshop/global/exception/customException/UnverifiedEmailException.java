package JOO.jooshop.global.exception.customException;

public class UnverifiedEmailException extends RuntimeException{

    // 예외 메시지를 지정해 생성
    public UnverifiedEmailException(String message) {
        super(message);
    }
}
