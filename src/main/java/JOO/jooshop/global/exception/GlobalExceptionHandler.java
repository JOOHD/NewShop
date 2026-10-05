package JOO.jooshop.global.exception;

import JOO.jooshop.global.exception.customException.*;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.security.InvalidParameterException;
import java.util.NoSuchElementException;
import java.util.Set;

@ControllerAdvice
public class GlobalExceptionHandler {

    // ===================== 400 Bad Request =====================
    // 잘못된 요청 파라미터, 상태 오류 등 클라이언트 책임

    // 잘못된 인자 → 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // @Valid 검증 실패 → 400 (첫 번째 필드 오류 메시지 사용)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = "유효성 검사 실패 : " + ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return buildResponse(HttpStatus.BAD_REQUEST, errorMessage);
    }

    // 요청 본문(JSON) 파싱 실패 → 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다 : " + ex.getMessage());
    }

    // 필수 값 누락(NPE) → 400
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ErrorResponse> handleNullPointerException(NullPointerException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "필수 필드입니다 : " + ex.getMessage());
    }

    // 잘못된 파라미터 → 400
    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameterException(InvalidParameterException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // 잘못된 상태에서의 요청(이미 처리된 계정 상태 변경 등) → 400
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // 요청 회원과 대상 회원 불일치 → 400
    @ExceptionHandler(MemberNotMatchException.class)
    public ResponseEntity<ErrorResponse> handleMemberNotMatchException(MemberNotMatchException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ResponseMessageConstants.MEMBER_NOT_MATCH);
    }

    // 비밀번호 불일치 등 인증 정보 오류 → 400
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handlerInvalidCredentialsException(InvalidCredentialsException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ResponseMessageConstants.CREDENTIALS_NOT_MATCH);
    }

    // 결제 취소 실패 → 400
    @ExceptionHandler(PaymentCancelFailureException.class)
    public ResponseEntity<ErrorResponse> handlePaymentCancelFailureException(PaymentCancelFailureException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ResponseMessageConstants.PAYMENT_CANCEL_FAILURE);
    }

    // Bean Validation 제약 위반 → 400 (위반 메시지를 모아서 반환)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        StringBuilder errorMessage = new StringBuilder();
        for (ConstraintViolation<?> violation : violations) {
            errorMessage.append(violation.getMessage()).append("\n");
        }
        return buildResponse(HttpStatus.BAD_REQUEST, errorMessage.toString());
    }

    // ===================== 401 Unauthorized =====================
    // 인증되지 않은 사용자 (토큰 없음/만료 등)

    // JWT 만료 → 401
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ErrorResponse> handleExpiredJwt(ExpiredJwtException e) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다.");
    }

    // 대상 데이터 없음(NoSuchElementException) → 404
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException e) {
        return buildResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // ===================== 🔐 403 Forbidden =====================
    // 인증은 되었으나 권한이 부족하거나 제한된 상태

    // 이메일 미인증 → 403
    @ExceptionHandler(UnverifiedEmailException.class)
    public ResponseEntity<ErrorResponse> handleUnverifiedEmail(UnverifiedEmailException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ResponseMessageConstants.EMAIL_NOT_VERIFIED);
    }

    // 이미 가입된 이메일 → 208
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handlerEmailAlreadyExists(EmailAlreadyExistsException ex) {
        return buildResponse(HttpStatus.ALREADY_REPORTED, ResponseMessageConstants.EMAIL_ALREADY_EXISTS);
    }

    // 이미 존재하는 회원 → 208
    @ExceptionHandler(ExistingMemberException.class)
    public ResponseEntity<ErrorResponse> handlerExistingMember(ExistingMemberException ex) {
        return buildResponse(HttpStatus.ALREADY_REPORTED, ResponseMessageConstants.MEMBER_ALREADY_EXISTS);
    }

    // 닉네임이 올바르지 않음 → 208
    @ExceptionHandler(InvalidNicknameException.class)
    public ResponseEntity<ErrorResponse> handleInvalidNicknameException(InvalidNicknameException ex) {
        return buildResponse(HttpStatus.ALREADY_REPORTED, ResponseMessageConstants.INVALID_NICKNAME);
    }

    // ===================== 기타 보안 예외 =====================

    // 접근 권한 없음(타인 정보 접근 등) → 403
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // ===================== 404 Not Found =====================

    // 결제 내역 없음 → 404
    @ExceptionHandler(PaymentHistoryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentHistoryNotFoundException(PaymentHistoryNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ResponseMessageConstants.PAYMENT_HISTORY_NOT_FOUND);
    }

    // 회원 없음 → 404
    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMemberNotFoundException(MemberNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ResponseMessageConstants.MEMBER_NOT_FOUND);
    }

    // ===================== 공통 응답 빌더 =====================

    // 상태 코드와 메시지로 공통 ErrorResponse 응답 생성
    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message));
    }
}
