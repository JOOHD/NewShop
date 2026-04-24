package JOO.jooshop.order.service;

import JOO.jooshop.cart.entity.Cart;
import JOO.jooshop.cart.repository.CartRepository;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.repository.MemberRepository;
import JOO.jooshop.order.entity.OrderProduct;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.entity.TemporaryOrderRedis;
import JOO.jooshop.order.model.OrderDto;
import JOO.jooshop.order.model.TempOrderResponse;
import JOO.jooshop.order.repository.OrderRepository;
import JOO.jooshop.order.repository.RedisOrderRepository;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.productManagement.entity.ProductManagement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static JOO.jooshop.global.authorization.MemberAuthorizationUtil.verifyUserIdMatch;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /*
     * [Service]

     * 기존
     * - 주문 생성 시 로직이 분산
     * - Cart → Order 변환 과정이 명확히 구조화되지 않음
     * - 엔티티 생성 로직이 서비스에 일부 포함
     *
     * refactoring 26.04
     * - Cart → Order 변환 흐름 명확화
     * - orderProductFromCart()로 스냅샷 생성
     * - Orders.createOrder()로 생성 책임 위임
     * - addOrderProduct()로 Aggregate 내부 관리
     */

    private final RedisOrderRepository redisOrderRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;

    /**
     * 로그인 사용자의 Redis 임시 주문 조회
     */
    public TempOrderResponse getTemporaryOrder(Long memberId) {
        TemporaryOrderRedis tempOrder = redisOrderRepository.findById("tempOrder:" + memberId)
                .orElseThrow(() -> new NoSuchElementException("임시 주문 정보가 없습니다."));

        return new TempOrderResponse(tempOrder);
    }

    /**
     * 주문 생성
     * - Cart 기반으로 Orders Aggregate 생성
     * - OrderProduct는 Orders가 편입
     * - Redis에 임시 주문 저장
     */
    public Orders createOrder(List<Long> cartIds, OrderDto orderDto) {
        List<Cart> carts = cartRepository.findAllById(cartIds);
        validateCarts(carts);

        Long memberId = carts.get(0).getMember().getId();
        verifyUserIdMatch(memberId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("회원 정보를 찾을 수 없습니다."));

        // dto는 보통 service가 이미 들고 있는 입력값 묶음
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

        carts.forEach(cart -> order.addOrderProduct(orderProductFromCart(cart)));

        saveTemporaryOrder(orderDto, carts, order);

        return order;
    }

    /**
     * 주문 확정
     * - Redis 임시 주문 기반
     * - Orders Aggregate 재생성 후 DB 저장
     */
    public Orders confirmOrder(OrderDto orderDto) {
        // 1. 임시 주문 정보 조회
        TemporaryOrderRedis tempOrder = redisOrderRepository.findById("tempOrder:" + orderDto.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("임시 주문 정보가 없습니다."));

        // 2. 주문자 조회
        Member member = memberRepository.findById(orderDto.getMemberId())
                .orElseThrow(() -> new NoSuchElementException("회원 정보를 찾을 수 없습니다."));

        // 3. 현재 로그인 유저 = 주문자 일치 검증
        verifyUserIdMatch(member.getId());

        // 4. 주문 엔티티 1개 생성
        // 엔티티가 아닌, dto에서 값을 가져오는 이유 = 서비스가 dto 값을 꺼내 엔티티에 전달
        Orders order = Orders.createOrder( // (= createOrder가 만든 Orders 객체)
                member,
                tempOrder.getOrdererName(),
                tempOrder.getPhoneNumber(),
                orderDto.getPostCode(),
                orderDto.getAddress(),
                orderDto.getDetailAddress(),
                orderDto.getPayMethod(),
                generateMerchantUid(orderDto)
        );

        // 5. tempOrder 안에 들어있던 cartId 목록을 꺼냄
        tempOrder.getCartIds().stream()
                .map(cartRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                // 6. cart -> OrderProduct
                // 7. order 1개 안에
                .forEach(cart -> order.addOrderProduct(orderProductFromCart(cart)));

        Orders savedOrder = orderRepository.save(order);
        log.info("주문 확정 완료: orderId={}", savedOrder.getOrderId());

        return savedOrder;
    }

    /**
     * Redis 임시 주문 저장
     */
    private void saveTemporaryOrder(OrderDto orderDto, List<Cart> carts, Orders order) {
        TemporaryOrderRedis tempOrder = TemporaryOrderRedis.createTemporaryOrder(
                orderDto.getMemberId(),
                orderDto.getUsername(),
                resolveOrdererName(orderDto, null),
                orderDto.getPhoneNumber(),
                carts.stream().map(Cart::getCartId).toList(),
                carts.stream()
                        .map(cart -> cart.getProductManagement().getProduct().getProductName())
                        .toList(),
                carts.stream()
                        .map(cart -> cart.getProductManagement().getSize() != null
                                ? cart.getProductManagement().getSize().name()
                                : null)
                        .toList(),
                carts.stream()
                        .map(cart -> extractThumbnailPath(cart.getProductManagement().getProduct()))
                        .toList(),
                carts.stream()
                        .map(Cart::getQuantity)
                        .toList(),
                order.getTotalPrice()
        );

        redisOrderRepository.save(tempOrder);
        log.info("임시 주문 Redis 저장 완료: key={}", tempOrder.getId());
    }

    /**
     * Cart -> OrderProduct 변환
     * 주문 시점 스냅샷 생성 책임은 서비스에서 조립
     */
    private OrderProduct orderProductFromCart(Cart cart) {
        ProductManagement pm = cart.getProductManagement();
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

    private void  validateCarts(List<Cart> carts) {
        if (carts == null || carts.isEmpty()) {
            throw new IllegalArgumentException("주문할 장바구니가 없습니다.");
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