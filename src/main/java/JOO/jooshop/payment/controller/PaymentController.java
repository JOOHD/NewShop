package JOO.jooshop.payment.controller;

import JOO.jooshop.global.exception.customException.OrderNotPayableException;
import JOO.jooshop.global.exception.customException.PaymentAmountMismatchException;
import JOO.jooshop.payment.model.PaymentCancelDto;
import JOO.jooshop.payment.model.PaymentHistoryDto;
import JOO.jooshop.payment.model.PaymentRequestDto;
import JOO.jooshop.payment.service.PaymentService;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.exception.IamportResponseException;
import com.siot.IamportRestClient.request.CancelData;
import com.siot.IamportRestClient.response.IamportResponse;
import com.siot.IamportRestClient.response.Payment;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 결제 API 컨트롤러 — 요청 수신 및 서비스 위임만 담당.
 * IamportClient는 IamportConfig에서 Bean으로 주입.
 */
@Tag(name = "결제", description = "Iamport 결제 요청/검증/취소")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final IamportClient iamportClient;

    // 결제 검증 — 아임포트 서버에서 결제 정보를 다시 조회해 금액 등 확인 후 결제 처리
    @PostMapping("/payment/{imp_uid}")
    public ResponseEntity<IamportResponse<Payment>> validateIamport(
            @PathVariable("imp_uid") String impUid,
            @RequestBody PaymentRequestDto request
    ) throws IamportResponseException, IOException {

        // 1) Iamport 서버에 실제 결제 정보 재조회 (클라이언트 데이터 신뢰 불가)
        IamportResponse<Payment> paymentResponse = iamportClient.paymentByImpUid(impUid);
        Payment iamportPayment = paymentResponse.getResponse();
        log.info("결제 요청 응답 - merchantUid={}", iamportPayment.getMerchantUid());

        // 2) 결제 완료 처리 — 서비스가 소유자 확인, 중복 방어, "실제 결제 금액 vs DB 주문 금액" 검증을 한다.
        //    (예전에는 여기서 "클라이언트가 보낸 금액 vs 실제 결제 금액"만 비교했는데, 둘 다 조작될 수 있어 기준이 될 수 없다)
        try {
            paymentService.processPaymentDone(iamportPayment, request);
        } catch (PaymentAmountMismatchException | OrderNotPayableException e) {
            // 금액이 다르거나 결제할 수 없는 주문(이미 결제됨/만료됨)에 들어온 결제는 이미 Iamport에서 승인(출금)된 상태라,
            // 예외만 던지면 고객 돈만 빠져나간다. 승인된 결제를 즉시 전액 취소(환불)한 뒤 거부한다.
            cancelMismatchedPayment(impUid);
            throw e;
        }

        return ResponseEntity.ok(paymentResponse);
    }

    // 금액이 맞지 않는 결제를 Iamport에서 자동 전액 취소. 취소 자체가 실패해도 원래 예외(400)는 그대로 던지도록 로그만 남긴다.
    private void cancelMismatchedPayment(String impUid) {
        try {
            IamportResponse<Payment> cancelResponse =
                    iamportClient.cancelPaymentByImpUid(new CancelData(impUid, true));
            if (cancelResponse.getCode() != 0) {
                log.error("금액 불일치 결제 자동 취소 거부 — impUid: {}, 사유: {} (수동 확인 필요)", impUid, cancelResponse.getMessage());
            } else {
                log.warn("금액 불일치 결제를 자동 취소했습니다 — impUid: {}", impUid);
            }
        } catch (IamportResponseException | IOException e) {
            log.error("금액 불일치 결제 자동 취소 실패 — impUid: {} (수동 확인 필요)", impUid, e);
        }
    }

    // 회원의 결제 내역 조회
    @GetMapping("/paymentHistory/{memberId}")
    public ResponseEntity<List<PaymentHistoryDto>> getPaymentHistories(@PathVariable Long memberId) {
        return ResponseEntity.ok(paymentService.getPaymentHistoriesByMemberId(memberId));
    }

    // 결제 취소 요청
    @PostMapping("/payment/cancel/{paymentHistoryId}")
    public ResponseEntity<IamportResponse<Payment>> paymentCancel(
            @PathVariable Long paymentHistoryId,
            @RequestBody PaymentCancelDto requestDto
    ) {
        // throws 제거 — PaymentService에서 RuntimeException으로 포장하므로
        // GlobalExceptionHandler가 일괄 처리
        IamportResponse<Payment> cancelResponse =
                paymentService.cancelPayment(paymentHistoryId, requestDto, iamportClient);

        return ResponseEntity.ok(cancelResponse);
    }
}
