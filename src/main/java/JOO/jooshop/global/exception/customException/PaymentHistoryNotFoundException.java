package JOO.jooshop.global.exception.customException;

public class PaymentHistoryNotFoundException extends RuntimeException {

    // 예외 메시지를 지정해 생성
    public PaymentHistoryNotFoundException(String message) {
        super(message);
    }
}
