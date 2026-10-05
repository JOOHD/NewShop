package JOO.jooshop.global.exception.customException;

public class ProductNotFoundException extends RuntimeException {
    // 예외 메시지를 지정해 생성
    public ProductNotFoundException(String message) {
        super(message);
    }
}
