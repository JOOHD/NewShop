package JOO.jooshop.global.exception.customException;

/**
 * 결제를 받을 수 없는 상태의 주문에 결제가 들어왔을 때 발생한다.
 * (이미 결제 완료된 주문에 다른 결제번호로 또 결제, 만료·취소된 주문에 뒤늦게 결제 등)
 *
 * 이미 Iamport에서는 승인된 결제이므로, 컨트롤러가 이 예외를 구분해 승인된 결제를 자동 취소(환불)한다.
 */
public class OrderNotPayableException extends IllegalStateException {

    public OrderNotPayableException(String message) {
        super(message);
    }
}
