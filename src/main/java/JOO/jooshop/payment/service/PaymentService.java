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
import JOO.jooshop.cart.entity.Cart;
import JOO.jooshop.cart.repository.CartRepository;
import JOO.jooshop.productVariant.repository.ProductVariantRepository;
import JOO.jooshop.payment.entity.PaymentRecord;
import JOO.jooshop.payment.entity.PaymentRefund;
import JOO.jooshop.payment.repository.PaymentRecordRepository;
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
import java.util.Set;
import java.util.stream.Collectors;

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
    private final PaymentRecordRepository paymentRecordRepository;
    private final CartRepository cartRepository;
    private final ProductVariantRepository productVariantRepository;

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
        //    PaymentRecord(결제 1건=1행, imp_uid UNIQUE)를 먼저 보고, 이 구조 이전에 만들어진 결제는 PaymentHistory로 확인한다.
        //    여기를 뚫고 들어와도 PaymentRecord 저장 시 UNIQUE 제약이 마지막으로 막는다.
        if (paymentRecordRepository.existsByImpUid(response.getImpUid())
                || paymentRepository.existsByImpUid(response.getImpUid())) {
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

        // 결제 1건 = 1행으로 기록 (imp_uid UNIQUE — 같은 결제가 두 번 저장되면 DB가 거부한다)
        paymentRecordRepository.save(PaymentRecord.paid(
                member, order, response.getImpUid(), response.getPayMethod(), order.getPayAmount()));

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

        // 결제가 끝났으니 이 주문에 쓴 장바구니 항목을 비운다 (바로 구매는 장바구니가 없어 해당 없음)
        clearPaidCarts(order, request);
    }

    /**
     * 결제 완료된 주문에 쓰인 장바구니 항목 삭제.
     *
     * 화면이 보낸 cartIds는 그대로 믿지 않는다. 다음 조건을 모두 만족하는 항목만 지운다.
     *   1) 요청한 회원 본인의 장바구니 (남의 장바구니는 건드리지 않음)
     *   2) 이 주문에 실제로 담긴 옵션과 같은 옵션 (주문과 무관한 장바구니 항목은 남김)
     */
    private void clearPaidCarts(Orders order, PaymentRequestDto request) {
        List<Long> cartIds = request.getInventoryIdList();
        if (cartIds == null || cartIds.isEmpty()) {
            return;
        }

        Set<Long> orderedVariantIds = order.getOrderProducts().stream()
                .map(orderProduct -> orderProduct.getProductVariant().getVariantId())
                .collect(Collectors.toSet());

        List<Cart> paidCarts = cartRepository.findOwnedCartsByIds(cartIds, order.getMember().getId()).stream()
                .filter(cart -> orderedVariantIds.contains(cart.getProductVariant().getVariantId()))
                .toList();

        cartRepository.deleteAll(paidCarts);
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
        // Iamport 취소는 결제 전체(imp_uid) 단위로 이뤄지므로, 같은 결제의 상품별 이력·결제 기록·주문 상태도 함께 취소 처리한다.
        // (예전에는 클릭한 이력 한 줄만 취소되어 나머지 줄은 취소 가능 상태로 남고, 주문은 COMPLETE로 남았다)
        String impUid = paymentHistory.getImpUid();
        paymentRepository.findAllByImpUid(impUid).stream()
                .filter(h -> h != paymentHistory && h.isCancelable())
                .forEach(PaymentHistory::markCanceled);
        paymentHistory.markCanceled();

        paymentRecordRepository.findByImpUid(impUid)
                .filter(record -> record.getStatus().isCancelable())
                .ifPresent(PaymentRecord::markCanceled);

        Orders paidOrder = paymentHistory.getOrders();
        if (paidOrder != null && paidOrder.getPaymentStatus() == PaymentStatus.COMPLETE) {
            paidOrder.markCanceled();

            // 결제 취소 = 출고 전 취소로 보고 주문 때 차감한 재고를 되돌린다.
            // 주문이 COMPLETE → CANCELED로 바뀌는 이번 한 번만 실행되므로 이중 복구가 없다.
            // 같은 옵션은 합산하고 옵션 ID 오름차순으로 처리한다 (만료 배치와 같은 방식).
            paidOrder.quantityByVariant().forEach(productVariantRepository::increaseStock);
        }

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
