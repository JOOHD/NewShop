package JOO.jooshop.order.service;

import JOO.jooshop.cart.entity.Cart;
import JOO.jooshop.cart.repository.CartRepository;
import JOO.jooshop.global.authentication.jwts.entity.CustomUserDetails;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.entity.enums.MemberRole;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.entity.enums.PayMethod;
import JOO.jooshop.order.model.OrderDto;
import JOO.jooshop.order.repository.OrderRepository;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.productVariant.entity.ProductVariant;
import JOO.jooshop.productVariant.entity.enums.Size;
import JOO.jooshop.productVariant.repository.ProductVariantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * OrderService 단위 테스트.
 *
 * [리팩토링 반영]
 * - 기존: saveTempOrder/getTempOrder/createOrder(memberId) — Redis 임시 주문 구조 테스트
 * - 현재: confirmOrder(OrderDto) 하나로 통합된 구조에 맞춰 재작성
 *
 * 핵심 검증 포인트:
 * 1. 정상 주문 확정 — Cart 조회 → Orders 생성 → DB 저장
 * 2. 장바구니가 비어있으면 예외 발생
 * 3. 본인 소유가 아닌 요청은 SecurityException 발생 (verifyUserIdMatch)
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MemberAccountService memberAccountService;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private OrderService orderService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ========================================================
    // 주문 확정
    // ========================================================
    @Nested
    @DisplayName("주문 확정")
    class ConfirmOrder {

        @Test
        @DisplayName("정상 주문 시 Cart 조회 후 Orders가 DB에 저장된다")
        void confirmOrder_success_savesOrder() {
            // given
            Long memberId = 1L;
            authenticateAs(memberId, MemberRole.USER);

            Member member = Member.registerGeneral(
                    "test@example.com", "encodedPw",
                    "홍길동", "길동이", "010-1234-5678", "uuid"
            );

            Cart cart = mock(Cart.class);
            ProductVariant pm = mock(ProductVariant.class);
            Product product = mock(Product.class);

            given(cart.getProductVariant()).willReturn(pm);
            given(cart.getQuantity()).willReturn(2);
            given(pm.getVariantId()).willReturn(100L);
            given(productVariantRepository.decreaseStockIfEnough(100L, 2L)).willReturn(1);
            given(pm.getProduct()).willReturn(product);
            given(pm.getSize()).willReturn(Size.M);
            given(product.getProductName()).willReturn("테스트 상품");
            given(product.getPrice()).willReturn(BigDecimal.valueOf(10000));
            given(product.getProductThumbnails()).willReturn(List.of());

            OrderDto orderDto = OrderDto.builder()
                    .memberId(memberId)
                    .cartIds(List.of(10L))
                    .postCode("12345")
                    .address("서울시 강남구")
                    .username("홍길동")
                    .payMethod(PayMethod.card)
                    .build();

            given(cartRepository.findAllById(orderDto.getCartIds())).willReturn(List.of(cart));
            given(memberAccountService.findMemberById(memberId)).willReturn(member);
            given(orderRepository.save(any(Orders.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            Orders result = orderService.confirmOrder(orderDto);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getOrderProducts()).hasSize(1);
            verify(orderRepository, times(1)).save(any(Orders.class));
            verify(productVariantRepository).decreaseStockIfEnough(100L, 2L);
        }

        @Test
        @DisplayName("바로 구매(items)는 장바구니를 거치지 않고 옵션에서 바로 주문이 만들어진다")
        void confirmOrder_buyNow_skipsCart() {
            // given
            Long memberId = 1L;
            authenticateAs(memberId, MemberRole.USER);

            Member member = Member.registerGeneral(
                    "test@example.com", "encodedPw",
                    "홍길동", "길동이", "010-1234-5678", "uuid"
            );

            ProductVariant pm = mock(ProductVariant.class);
            Product product = mock(Product.class);
            given(productVariantRepository.findById(100L)).willReturn(java.util.Optional.of(pm));
            given(pm.getVariantId()).willReturn(100L);
            given(productVariantRepository.decreaseStockIfEnough(100L, 3L)).willReturn(1);
            given(pm.getProduct()).willReturn(product);
            given(pm.getSize()).willReturn(Size.M);
            given(product.getProductName()).willReturn("테스트 상품");
            given(product.getPrice()).willReturn(BigDecimal.valueOf(10000));
            given(product.getProductThumbnails()).willReturn(List.of());

            OrderDto orderDto = OrderDto.builder()
                    .memberId(memberId)
                    .items(List.of(JOO.jooshop.order.model.OrderItemRequest.builder().variantId(100L).quantity(3).build()))
                    .postCode("12345")
                    .address("서울시 강남구")
                    .username("홍길동")
                    .payMethod(PayMethod.card)
                    .build();

            given(memberAccountService.findMemberById(memberId)).willReturn(member);
            given(orderRepository.save(any(Orders.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            Orders result = orderService.confirmOrder(orderDto);

            // then — 장바구니는 조회하지 않고, 가격은 서버(DB 상품)에서 읽어 3 x 10,000 = 30,000
            verify(cartRepository, org.mockito.Mockito.never()).findAllById(any());
            verify(productVariantRepository).decreaseStockIfEnough(100L, 3L);
            assertThat(result.getOrderProducts()).hasSize(1);
            assertThat(result.getTotalPrice()).isEqualByComparingTo("30000");
        }

        @Test
        @DisplayName("재고가 부족하면 IllegalStateException이 발생하고 주문은 저장되지 않는다")
        void confirmOrder_insufficientStock_throwsException() {
            // given
            Long memberId = 1L;
            authenticateAs(memberId, MemberRole.USER);

            Cart cart = mock(Cart.class);
            ProductVariant pm = mock(ProductVariant.class);
            given(cart.getProductVariant()).willReturn(pm);
            given(cart.getQuantity()).willReturn(3);
            given(pm.getVariantId()).willReturn(100L);
            given(productVariantRepository.decreaseStockIfEnough(100L, 3L)).willReturn(0); // 조건부 UPDATE 0건 = 재고 부족

            OrderDto orderDto = OrderDto.builder()
                    .memberId(memberId)
                    .cartIds(List.of(10L))
                    .postCode("12345")
                    .address("서울시 강남구")
                    .username("홍길동")
                    .payMethod(PayMethod.card)
                    .build();

            given(cartRepository.findAllById(orderDto.getCartIds())).willReturn(List.of(cart));
            given(memberAccountService.findMemberById(memberId)).willReturn(
                    Member.registerGeneral("test@example.com", "encodedPw", "홍길동", "길동이", "010-1234-5678", "uuid"));

            // when & then
            assertThatThrownBy(() -> orderService.confirmOrder(orderDto))
                    .isInstanceOf(IllegalStateException.class);

            verify(orderRepository, never()).save(any(Orders.class));
        }

        @Test
        @DisplayName("장바구니가 비어있으면 IllegalArgumentException 발생")
        void confirmOrder_emptyCart_throwsException() {
            // given — 장바구니 검증이 인증 검증보다 먼저 실행되므로 로그인 목킹은 불필요
            Long memberId = 1L;

            OrderDto orderDto = OrderDto.builder()
                    .memberId(memberId)
                    .cartIds(List.of(999L))
                    .postCode("12345")
                    .address("서울시 강남구")
                    .username("홍길동")
                    .build();

            given(cartRepository.findAllById(orderDto.getCartIds())).willReturn(List.of());

            // when & then
            assertThatThrownBy(() -> orderService.confirmOrder(orderDto))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(orderRepository, never()).save(any(Orders.class));
        }

        @Test
        @DisplayName("로그인한 사용자와 요청 memberId가 다르면 SecurityException 발생")
        void confirmOrder_memberIdMismatch_throwsSecurityException() {
            // given — 로그인은 2L인데 요청은 1L 소유의 장바구니
            authenticateAs(2L, MemberRole.USER);

            Cart cart = mock(Cart.class);

            OrderDto orderDto = OrderDto.builder()
                    .memberId(1L)
                    .cartIds(List.of(10L))
                    .postCode("12345")
                    .address("서울시 강남구")
                    .username("홍길동")
                    .build();

            given(cartRepository.findAllById(orderDto.getCartIds())).willReturn(List.of(cart));

            // when & then
            assertThatThrownBy(() -> orderService.confirmOrder(orderDto))
                    .isInstanceOf(SecurityException.class);

            verify(orderRepository, never()).save(any(Orders.class));
        }
    }

    // ========================================================
    // 헬퍼 — SecurityContext에 로그인 사용자 주입
    // ========================================================
    private void authenticateAs(Long memberId, MemberRole role) {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        given(userDetails.getMemberId()).willReturn(memberId);
        given(userDetails.getMemberRole()).willReturn(role);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
