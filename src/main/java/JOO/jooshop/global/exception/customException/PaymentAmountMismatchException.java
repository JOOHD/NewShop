package JOO.jooshop.global.exception.customException;

/**
 * 실제 결제된 금액이 DB의 주문 금액과 다를 때 발생한다.
 * IllegalArgumentException을 상속해 기존 예외 처리(400 응답)를 그대로 따르고,
 * 컨트롤러가 이 예외를 구분해서 이미 승인된 결제를 자동 취소(환불)할 수 있게 한다.
 */
public class PaymentAmountMismatchException extends IllegalArgumentException {

    public PaymentAmountMismatchException(String message) {
        super(message);
    }
}
