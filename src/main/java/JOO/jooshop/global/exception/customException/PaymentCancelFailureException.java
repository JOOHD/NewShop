package JOO.jooshop.global.exception.customException;

public class PaymentCancelFailureException extends RuntimeException  {

    // 예외 메시지를 지정해 생성
    public PaymentCancelFailureException(String message) {
        super(message);
    }
}

