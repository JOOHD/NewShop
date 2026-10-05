package JOO.jooshop.global.exception.customException;

public class ExistingMemberException extends RuntimeException {

    // 예외 메시지를 지정해 생성
    public ExistingMemberException(String message) {
        super(message);
    }
}
