package JOO.jooshop.payment.service;

import JOO.jooshop.global.exception.customException.OrderNotFoundException;
import JOO.jooshop.global.exception.customException.OrderNotPayableException;
import JOO.jooshop.global.exception.customException.PaymentAmountMismatchException;
import JOO.jooshop.payment.entity.PaymentStatus;
import JOO.jooshop.global.exception.customException.PaymentCancelFailureException;
import JOO.jooshop.global.exception.customException.PaymentHistoryNotFoundException;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.order.entity.OrderProduct;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.repository.OrderRepository;
import JOO.jooshop.payment.entity.PaymentHistory;
import JOO.jooshop.payment.entity.PaymentRefund;
import JOO.jooshop.payment.model.PaymentCancelDto;
import JOO.jooshop.payment.model.PaymentHistoryDto;
import JOO.jooshop.payment.model.PaymentRequestDto;
import JOO.jooshop.payment.repository.PaymentRefundRepository;
import JOO.jooshop.payment.repository.PaymentRepository;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.exception.IamportResponseException;
import com.siot.IamportRestClient.request.CancelData;
import com.siot.IamportRestClient.response.IamportResponse;
import com.siot.IamportRestClient.response.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

import static JOO.jooshop.global.authorization.MemberAuthorizationUtil.verifyUserIdMatch;

/**
 * [PaymentService 트랜잭션 전략]
 *
 * 클래스 기본값: @Transactional
 *   - rollbackFor = Exception.class 제거
 *   - 이유: cancelPayment의 Checked Exception(IOException, IamportResponseException)은
 *     try-catch에서 이미 RuntimeException(PaymentCancelFailureException)으로 변환됨
 *     → 클래스 밖으로 Checked Exception이 나가지 않음 → rollbackFor 불필요
 *   - processPaymentDone도 Checked Exception을 직접 던지지 않음
 *
 * 조회 메서드: @Transactional(readOnly = true)로 override
 *   - 클래스 기본 @Transactional을 통째로 교체
 *   - Dirty Checking 비활성화 → 성능 최적화
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final OrderRepository orderRepository;
    private final MemberAccountService memberAccountService;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;

    /**
     * 결제 완료 처리
     * Order 상태 변경 + PaymentHistory 저장이 하나의 트랜잭션
     * 중간 실패 시 전부 롤백
     *
     * [정리] 예전 Redis 2단계 주문 흐름(TemporaryOrderRedis) 잔재였던
     * "cartIds:"/"tempOrder:" Redis 키 삭제 로직 제거 — 지금 구조에선 그 키를
     * 아무도 저장하지 않아 매번 존재하지 않는 키를 지우려는 의미 없는 호출이었음.
     */
    public void processPaymentDone(Payment response, PaymentRequestDto request) {
        verifyUserIdMatch(request.getMemberId());

        // 주문 행을 잠그고 조회 — 같은 주문의 결제 검증이 동시에 와도 한 번에 하나씩 처리된다.
        // (뒤의 existsByImpUid 중복 확인과 저장 사이에 다른 요청이 끼어들지 못하게 함. 락은 트랜잭션 종료 시 해제)
        Orders order = orderRepository.findByIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException("주문 정보를 찾을 수 없습니다."));

        // 1) 요청한 회원이 이 주문의 주인인지 확인 (남의 주문에 결제 완료 처리 방지)
        if (!order.getMember().getId().equals(request.getMemberId())) {
            throw new SecurityException("본인의 주문이 아닙니다.");
        }

        // 2) 이 결제가 정말 이 주문의 결제인지 확인 — 서버가 발급한 주문번호(merchantUid)가 같아야 한다.
        //    다른 주문의 결제를 가져다 쓰는 것을 막는다. 이 결제는 이 주문 것이 아니므로 자동 취소하지 않고 거부만 한다
        //    (남의 정상 결제를 우리가 환불시키는 사고 방지).
        if (!order.getMerchantUid().equals(response.getMerchantUid())) {
            log.warn("주문번호 불일치 — 주문: {}, 결제: {}", order.getMerchantUid(), response.getMerchantUid());
            throw new IllegalArgumentException("결제의 주문번호가 이 주문과 일치하지 않습니다.");
        }

        // 3) 같은 결제번호(imp_uid)가 이미 처리됐다면 다시 처리하지 않는다 (멱등성: 중복 요청·재시도 방어)
        if (paymentRepository.existsByImpUid(response.getImpUid())) {
            log.info("이미 처리된 결제입니다 — impUid: {}", response.getImpUid());
            return;
        }

        // 4) 결제 대기(PENDING) 주문만 결제할 수 있다.
        //    이미 완료된 주문에 다른 결제번호로 또 결제했거나, 만료·취소된 주문에 뒤늦게 결제한 경우 → 승인된 결제는 환불 대상
        if (order.getPaymentStatus() != PaymentStatus.PENDING) {
            log.warn("결제할 수 없는 주문 상태 — orderId: {}, status: {}", order.getOrderId(), order.getPaymentStatus());
            throw new OrderNotPayableException("결제할 수 없는 주문입니다. (상태: " + order.getPaymentStatus() + ")");
        }

        // 5) Iamport가 확인한 실제 결제 금액과 DB에 저장된 결제 금액(상품 합계 + 배송비)을 비교
        //    (클라이언트가 보낸 금액만 믿으면 100원 결제 후 주문 전체를 완료 처리할 수 있음)
        if (order.getPayAmount().compareTo(response.getAmount()) != 0) {
            log.warn("결제 금액 불일치 — 주문 금액: {}, 실제 결제 금액: {}", order.getPayAmount(), response.getAmount());
            throw new PaymentAmountMismatchException("결제 금액이 주문 금액과 일치하지 않습니다.");
        }

        Member member = memberAccountService.findMemberById(request.getMemberId());

        order.changePaymentStatus(PaymentStatus.COMPLETE);

        List<OrderProduct> orderProducts = order.getOrderProducts();

        for (OrderProduct orderProduct : orderProducts) {
            PaymentHistory paymentHistory = PaymentHistory.createPaymentHistory(
                    member,
                    order,
                    orderProduct,
                    response.getImpUid(),
                    response.getPayMethod(),
                    order.getPayAmount(),   // 실제 결제 금액(배송비 포함) — 취소 시 이 금액으로 환불한다
                    response.getBankCode(),
                    response.getBankName(),
                    response.getBuyerAddr(),
                    response.getBuyerEmail()
            );
            paymentRepository.save(paymentHistory);
        }
    }

    /**
     * 결제 이력 조회
     *
     * 클래스 기본 @Transactional을 readOnly=true로 override
     * → "이 메서드는 읽기 전용" 명시 + Dirty Checking 비활성화
     */
    @Transactional(readOnly = true)
    public List<PaymentHistoryDto> getPaymentHistoriesByMemberId(Long memberId) {
        verifyUserIdMatch(memberId);

        return paymentRepository.findAllByMember_Id(memberId).stream()
                .map(PaymentHistoryDto::from)
                .toList();
    }

    /**
     * 결제 취소
     *
     * Iamport Checked Exception(IOException, IamportResponseException)을
     * try-catch로 잡아 RuntimeException(PaymentCancelFailureException)으로 변환
     * → 클래스 밖으로 Checked Exception이 나가지 않음
     * → rollbackFor = Exception.class 없어도 RuntimeException이 트랜잭션 롤백 트리거
     *
     * 순서 중요:
     *   Iamport API 호출 성공 확인 → 그 다음에 DB 변경(markCanceled())
     *   API 실패 시점엔 DB 변경이 없으므로 롤백해도 안전
     */
    public IamportResponse<Payment> cancelPayment(
            Long paymentHistoryId,
            PaymentCancelDto requestDto,
            IamportClient iamportClient
    ) {
        PaymentHistory paymentHistory = paymentRepository.findById(paymentHistoryId)
                .orElseThrow(() -> new PaymentHistoryNotFoundException("결제 내역을 찾을 수 없습니다."));

        // 본인의 결제(또는 관리자)만 취소 가능 — paymentHistoryId만 바꿔 남의 결제를 취소하는 것을 방지
        verifyUserIdMatch(paymentHistory.getMember().getId());

        if (!paymentHistory.isCancelable()) {
            throw new IllegalStateException("이미 취소되었거나 취소할 수 없는 결제입니다.");
        }

        CancelData cancelData = new CancelData(
                paymentHistory.getImpUid(),
                true,
                paymentHistory.getTotalPrice()
        );

        // Checked Exception → RuntimeException 변환
        // 이 시점 DB 변경 없음 → 예외 발생 시 롤백해도 안전
        IamportResponse<Payment> cancelResponse;
        try {
            cancelResponse = iamportClient.cancelPaymentByImpUid(cancelData);
        } catch (IamportResponseException | IOException e) {
            log.error("Iamport 환불 API 실패 — impUid: {}, 원인: {}", paymentHistory.getImpUid(), e.getMessage());
            throw new PaymentCancelFailureException("환불 처리 중 오류가 발생했습니다: " + e.getMessage());
        }

        if (cancelResponse.getCode() != 0) {
            throw new PaymentCancelFailureException("환불이 거부되었습니다: " + cancelResponse.getMessage());
        }

        // API 성공 확인 후에만 DB 변경 — 순서 핵심
        paymentHistory.markCanceled();

        PaymentRefund refund = PaymentRefund.createRefund(
                paymentHistory,
                requestDto.getReason(),
                null,
                requestDto.getRefundHolder(),
                requestDto.getRefundBank(),
                requestDto.getRefundAccount()
        );
        paymentRefundRepository.save(refund);

        return cancelResponse;
    }

}
