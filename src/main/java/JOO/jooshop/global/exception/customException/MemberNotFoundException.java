package JOO.jooshop.global.exception.customException;

public class MemberNotFoundException extends RuntimeException {

    // 예외 메시지를 지정해 생성
    public MemberNotFoundException(String message) {
        super(message);
    }
}
