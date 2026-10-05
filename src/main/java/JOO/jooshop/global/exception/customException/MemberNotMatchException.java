package JOO.jooshop.global.exception.customException;

public class MemberNotMatchException extends RuntimeException{

    // 예외 메시지를 지정해 생성
    public MemberNotMatchException(String message) {
        super(message);
    }
}
