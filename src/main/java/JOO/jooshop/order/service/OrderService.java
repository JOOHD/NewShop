package JOO.jooshop.order.service;

import JOO.jooshop.cart.entity.Cart;
import JOO.jooshop.cart.repository.CartRepository;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.order.entity.OrderProduct;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.model.OrderDto;
import JOO.jooshop.order.repository.OrderRepository;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.productVariant.entity.ProductVariant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
        // 1. 장바구니 조회 및 검증
        List<Cart> carts = cartRepository.findAllById(orderDto.getCartIds());
        validateCarts(carts);

        // 2. 본인 장바구니인지 검증
        Long memberId = orderDto.getMemberId();
        verifyUserIdMatch(memberId);

        // 3. 회원 조회
        Member member = memberAccountService.findMemberById(memberId);

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

        // 5. 장바구니 → 주문 상품 변환 후 추가
        carts.forEach(cart -> order.addOrderProduct(orderProductFromCart(cart)));

        // 6. DB 저장 — 이 시점부터 예외 시 전체 롤백
        Orders savedOrder = orderRepository.save(order);
        log.info("주문 확정 완료: orderId={}, memberId={}", savedOrder.getOrderId(), memberId);

        return savedOrder;
    }

    // ────────────────── private helpers ──────────────────

    private OrderProduct orderProductFromCart(Cart cart) {
        ProductVariant pm = cart.getProductVariant();
        Product product = pm.getProduct();

        String productSize = pm.getSize() != null ? pm.getSize().name() : null;
        String productImages = extractThumbnailPath(product);

        return OrderProduct.createOrderProduct(
                pm,
                product.getProductName(),
                productSize,
                productImages,
                product.getPrice(),
                cart.getQuantity()
        );
    }

    private String extractThumbnailPath(Product product) {
        return product.getProductThumbnails().isEmpty()
                ? null
                : product.getProductThumbnails().get(0).getImagesPath();
    }

    private void validateCarts(List<Cart> carts) {
        if (carts == null || carts.isEmpty()) {
            throw new IllegalArgumentException("주문할 장바구니 항목이 없습니다.");
        }
    }

    private String resolveOrdererName(OrderDto orderDto, Member member) {
        if (orderDto.getOrdererName() != null && !orderDto.getOrdererName().isBlank()) {
            return orderDto.getOrdererName();
        }
        if (member != null && member.getUsername() != null) {
            return member.getUsername();
        }
        return orderDto.getUsername();
    }

    private String resolvePhoneNumber(OrderDto orderDto, Member member) {
        if (orderDto.getPhoneNumber() != null && !orderDto.getPhoneNumber().isBlank()) {
            return orderDto.getPhoneNumber();
        }
        if (member != null) {
            return member.getPhoneNumber();
        }
        return null;
    }

    private String generateMerchantUid(OrderDto orderDto) {
        if (orderDto.getMerchantUid() != null && !orderDto.getMerchantUid().isBlank()) {
            return orderDto.getMerchantUid();
        }
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
