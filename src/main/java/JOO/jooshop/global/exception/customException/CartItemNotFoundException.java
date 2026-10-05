package JOO.jooshop.global.exception.customException;

public class CartItemNotFoundException extends RuntimeException {
    // 예외 메시지를 지정해 생성
    public CartItemNotFoundException(String message) {
        super(message);
    }
}
