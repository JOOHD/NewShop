package JOO.jooshop.payment.controller;

import JOO.jooshop.payment.model.PaymentCancelDto;
import JOO.jooshop.payment.model.PaymentHistoryDto;
import JOO.jooshop.payment.model.PaymentRequestDto;
import JOO.jooshop.payment.service.PaymentService;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.exception.IamportResponseException;
import com.siot.IamportRestClient.response.IamportResponse;
import com.siot.IamportRestClient.response.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * 결제 API 컨트롤러 — 요청 수신 및 서비스 위임만 담당.
 * IamportClient는 IamportConfig에서 Bean으로 주입.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final IamportClient iamportClient;

    @PostMapping("/payment/{imp_uid}")
    public ResponseEntity<IamportResponse<Payment>> validateIamport(
            @PathVariable("imp_uid") String impUid,
            @RequestBody PaymentRequestDto request
    ) throws IamportResponseException, IOException {

        // 1) Iamport 서버에 실제 결제 정보 재조회 (클라이언트 데이터 신뢰 불가)
        IamportResponse<Payment> paymentResponse = iamportClient.paymentByImpUid(impUid);
        Payment iamportPayment = paymentResponse.getResponse();
        log.info("결제 요청 응답 - merchantUid={}", iamportPayment.getMerchantUid());

        // 2) 금액 위조 검증: 클라이언트가 보낸 금액 vs Iamport가 확인한 실제 결제 금액
        BigDecimal iamportAmount = iamportPayment.getAmount();          // Iamport 실제 결제 금액
        BigDecimal requestAmount = request.getPrice();                   // 클라이언트 요청 금액
        if (iamportAmount.compareTo(requestAmount) != 0) {
            log.warn("결제 금액 불일치 — 요청: {}, Iamport: {}", requestAmount, iamportAmount);
            throw new IllegalArgumentException("결제 금액이 일치하지 않습니다.");
        }

        // 3) 검증 통과 후 결제 완료 처리
        paymentService.processPaymentDone(iamportPayment, request);

        return ResponseEntity.ok(paymentResponse);
    }

    @GetMapping("/paymentHistory/{memberId}")
    public ResponseEntity<List<PaymentHistoryDto>> getPaymentHistories(@PathVariable Long memberId) {
        return ResponseEntity.ok(paymentService.getPaymentHistoriesByMemberId(memberId));
    }

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
