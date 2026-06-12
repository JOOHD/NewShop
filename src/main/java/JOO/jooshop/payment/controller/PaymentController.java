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

        IamportResponse<Payment> paymentResponse = iamportClient.paymentByImpUid(impUid);
        log.info("결제 요청 응답 - merchantUid={}", paymentResponse.getResponse().getMerchantUid());

        paymentService.processPaymentDone(paymentResponse.getResponse(), request);

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
    ) throws IamportResponseException, IOException {

        IamportResponse<Payment> cancelResponse =
                paymentService.cancelPayment(paymentHistoryId, requestDto, iamportClient);

        return ResponseEntity.ok(cancelResponse);
    }
}
