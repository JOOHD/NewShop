package JOO.jooshop.order.service;

import JOO.jooshop.cart.entity.Cart;
import JOO.jooshop.cart.repository.CartRepository;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.order.entity.OrderProduct;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.model.OrderDto;
import JOO.jooshop.order.model.OrderListResponse;
import JOO.jooshop.order.repository.OrderRepository;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.productVariant.entity.ProductVariant;
import JOO.jooshop.productVariant.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static JOO.jooshop.global.authorization.MemberAuthorizationUtil.verifyUserIdMatch;

/**
 * [OrderService 리팩토링 — Before/After]
 *
 * Before:
 *   - createOrder()  : Cart 조회 → Redis 임시 저장
 *   - confirmOrder() : Redis 읽기 → DB 저장 (두 단계)
 *   - getTemporaryOrder() : Redis 조회
 *   → 문제: 클론 코딩에서 가져온 구조. 일반 쇼핑몰에서 Redis 임시주문은 불필요.
 *           흐름 복잡, 코드 증가, Redis 의존도 불필요하게 높음.
 *
 * After:
 *   - confirmOrder() 하나로 통합: Cart 조회 → 주문 생성 → DB 직접 저장
 *   → 단순하고 직관적. 실무 일반 쇼핑몰 패턴.
 *   → Redis는 "최근 본 상품(RecentlyViewedService)"에 집중 — 명확한 역할 분리.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final MemberAccountService memberAccountService;
    private final ProductVariantRepository productVariantRepository;

    /**
     * 주문 확정 — Cart → Orders DB 직접 저장
     *
     * Before: Redis 임시 주문을 꺼내서 DB 저장 (2단계)
     * After:  cartIds + 배송/결제 정보를 받아 바로 DB 저장 (1단계)
     *
     * @Transactional(rollbackFor = Exception.class)
     *   - 클래스 기본값(readOnly=true) override
     *   - Cart 조회 + Orders 저장 + OrderProduct 저장이 하나의 트랜잭션
     *   - 중간 예외 시 전부 롤백 → 부분 주문 데이터 DB에 남지 않음
     */
    @Transactional(rollbackFor = Exception.class)
    public Orders confirmOrder(OrderDto orderDto) {
        // 1. 주문 항목 결정 — 바로 구매(items)면 옵션·수량에서, 아니면 장바구니(cartIds)에서
        List<OrderLine> lines = resolveOrderLines(orderDto);

        // 2. 본인 요청인지 검증
        Long memberId = orderDto.getMemberId();
        verifyUserIdMatch(memberId);

        // 2-1. 장바구니 항목의 주인이 요청한 회원인지 검증 — cartIds는 클라이언트가 보내는 값이라
        //      남의 장바구니 ID를 넣어 보낼 수 있다 (memberId 일치만으로는 막을 수 없다)
        verifyCartOwner(lines, memberId);

        // 3. 회원 조회
        Member member = memberAccountService.findMemberById(memberId);

        // 3-1. 재고 차감 — 부족하면 예외로 전체 롤백 (주문·재고 모두 원복)
        decreaseStocks(lines);

        // 4. 주문 생성 (팩토리 메서드 — new Orders() 직접 생성 금지)
        Orders order = Orders.createOrder(
                member,
                resolveOrdererName(orderDto, member),
                resolvePhoneNumber(orderDto, member),
                orderDto.getPostCode(),
                orderDto.getAddress(),
                orderDto.getDetailAddress(),
                orderDto.getPayMethod(),
                generateMerchantUid(orderDto)
        );

        // 5. 주문 항목 → 주문 상품(주문 당시 스냅샷) 변환 후 추가
        lines.forEach(line -> order.addOrderProduct(orderProductFromLine(line)));

        // 6. DB 저장 — 이 시점부터 예외 시 전체 롤백
        Orders savedOrder = orderRepository.save(order);
        log.info("주문 확정 완료: orderId={}, memberId={}", savedOrder.getOrderId(), memberId);

        return savedOrder;
    }

    /**
     * 회원 본인의 주문내역 목록 조회 (최신순)
     */
    public List<OrderListResponse> getMyOrders(Long memberId) {
        return orderRepository.findAllByMemberIdOrderByOrderDayDesc(memberId).stream()
                .map(OrderListResponse::from)
                .toList();
    }

    // ────────────────── private helpers ──────────────────

    /**
     * 장바구니 수량만큼 옵션 재고를 차감한다.
     * - 같은 옵션이 여러 장바구니 항목에 있으면 수량을 합산
     * - 옵션 ID 오름차순으로 처리 → 여러 옵션을 동시에 주문하는 요청끼리 락 순서가 같아져 데드락 방지
     * - 조건부 UPDATE(재고 >= 수량)가 0건이면 재고 부족 → IllegalStateException (트랜잭션 롤백)
     */
    private void decreaseStocks(List<OrderLine> lines) {
        Map<Long, Long> qtyByVariant = new TreeMap<>();
        for (OrderLine line : lines) {
            qtyByVariant.merge(line.variant().getVariantId(), (long) line.quantity(), Long::sum);
        }

        qtyByVariant.forEach((variantId, qty) -> {
            if (productVariantRepository.decreaseStockIfEnough(variantId, qty) == 0) {
                throw new IllegalStateException("재고가 부족합니다. (옵션 ID: " + variantId + ")");
            }
            productVariantRepository.markSoldOutIfEmpty(variantId);
        });
    }

    // 주문할 옵션 + 수량 (장바구니 주문과 바로 구매가 공통으로 쓰는 중간 표현)
    // cart: 장바구니에서 온 항목이면 그 Cart(소유자 확인용), 바로 구매면 null
    private record OrderLine(ProductVariant variant, int quantity, Cart cart) {}

    // 바로 구매(items)가 있으면 그것을, 없으면 장바구니(cartIds)를 주문 항목으로 바꾼다
    private List<OrderLine> resolveOrderLines(OrderDto orderDto) {
        if (orderDto.getItems() != null && !orderDto.getItems().isEmpty()) {
            return orderDto.getItems().stream()
                    .map(item -> {
                        ProductVariant variant = productVariantRepository.findById(item.getVariantId())
                                .orElseThrow(() -> new IllegalArgumentException("상품 옵션을 찾을 수 없습니다."));
                        return new OrderLine(variant, item.getQuantity(), null);
                    })
                    .toList();
        }

        List<Cart> carts = cartRepository.findAllById(orderDto.getCartIds());
        validateCarts(carts);
        return carts.stream()
                .map(cart -> new OrderLine(cart.getProductVariant(), cart.getQuantity(), cart))
                .toList();
    }

    // 장바구니에서 온 항목은 모두 요청 회원의 장바구니여야 한다 (바로 구매 항목은 cart가 null이라 건너뜀)
    private void verifyCartOwner(List<OrderLine> lines, Long memberId) {
        for (OrderLine line : lines) {
            Cart cart = line.cart();
            if (cart != null && !memberId.equals(cart.getMember().getId())) {
                log.warn("남의 장바구니로 주문 시도 — 요청 memberId: {}, cartId: {}", memberId, cart.getCartId());
                throw new SecurityException("본인의 장바구니가 아닙니다.");
            }
        }
    }

    // 주문 항목을 주문 상품(주문 당시 가격/이름/이미지 스냅샷)으로 변환
    private OrderProduct orderProductFromLine(OrderLine line) {
        ProductVariant pm = line.variant();
        Product product = pm.getProduct();

        String productSize = pm.getSize() != null ? pm.getSize().name() : null;
        String productImages = extractThumbnailPath(product);

        return OrderProduct.createOrderProduct(
                pm,
                product.getProductName(),
                productSize,
                productImages,
                product.getPrice(),
                line.quantity()
        );
    }

    // 상품의 첫 번째 썸네일 경로 (없으면 null)
    private String extractThumbnailPath(Product product) {
        return product.getProductThumbnails().isEmpty()
                ? null
                : product.getProductThumbnails().get(0).getImagesPath();
    }

    // 주문할 장바구니 항목이 비어 있지 않은지 검증
    private void validateCarts(List<Cart> carts) {
        if (carts == null || carts.isEmpty()) {
            throw new IllegalArgumentException("주문할 장바구니 항목이 없습니다.");
        }
    }

    // 주문자명 결정 — 요청값, 없으면 회원 이름
    private String resolveOrdererName(OrderDto orderDto, Member member) {
        if (orderDto.getOrdererName() != null && !orderDto.getOrdererName().isBlank()) {
            return orderDto.getOrdererName();
        }
        if (member != null && member.getUsername() != null) {
            return member.getUsername();
        }
        return orderDto.getUsername();
    }

    // 연락처 결정 — 요청값, 없으면 회원 연락처
    private String resolvePhoneNumber(OrderDto orderDto, Member member) {
        if (orderDto.getPhoneNumber() != null && !orderDto.getPhoneNumber().isBlank()) {
            return orderDto.getPhoneNumber();
        }
        if (member != null) {
            return member.getPhoneNumber();
        }
        return null;
    }

    // 주문번호(merchant_uid)는 항상 서버가 발급한다 — "시각-랜덤" 형식.
    // 클라이언트가 보낸 값은 쓰지 않는다 (중복·위조 가능, 결제 검증에서 이 값으로 주문과 결제를 묶기 때문).
    private String generateMerchantUid(OrderDto orderDto) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
