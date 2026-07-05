package JOO.jooshop.order.service;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.model.OrderRequestDto;
import JOO.jooshop.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * OrderService 단위 테스트.
 *
 * 핵심 검증 포인트:
 * 1. 임시 주문(Redis 저장) 로직
 * 2. 주문 생성 — DB 저장 + Redis 삭제
 * 3. 주문 조회 — 본인 것만 조회 가능
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MemberAccountService memberAccountService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private OrderService orderService;

    // ========================================================
    // 임시 주문 저장 (Redis)
    // ========================================================
    @Nested
    @DisplayName("임시 주문")
    class TempOrder {

        @Test
        @DisplayName("임시 주문 저장 시 Redis에 저장된다")
        void saveTempOrder_savesToRedis() {
            // given
            Long memberId = 1L;
            OrderRequestDto requestDto = createOrderRequest(memberId, BigDecimal.valueOf(50000));

            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            // when
            orderService.saveTempOrder(memberId, requestDto);

            // then — Redis에 저장 호출 검증
            verify(valueOperations, times(1)).set(
                    eq("tempOrder:" + memberId),
                    any(),
                    any(),
                    any()
            );
        }

        @Test
        @DisplayName("임시 주문 조회 시 Redis에서 반환된다")
        void getTempOrder_returnsFromRedis() {
            // given
            Long memberId = 1L;
            OrderRequestDto storedDto = createOrderRequest(memberId, BigDecimal.valueOf(50000));

            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("tempOrder:" + memberId)).willReturn(storedDto);

            // when
            OrderRequestDto result = orderService.getTempOrder(memberId);

            // then
            assertThat(result.getTotalPrice()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        }
    }

    // ========================================================
    // 주문 생성
    // ========================================================
    @Nested
    @DisplayName("주문 생성")
    class CreateOrder {

        @Test
        @DisplayName("정상 주문 생성 시 DB에 저장되고 Redis 임시 주문이 삭제된다")
        void createOrder_success_savesAndClearsRedis() {
            // given
            Long memberId = 1L;
            Member member = createMember(memberId);
            OrderRequestDto requestDto = createOrderRequest(memberId, BigDecimal.valueOf(50000));

            given(memberAccountService.findMemberById(memberId)).willReturn(member);
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("tempOrder:" + memberId)).willReturn(requestDto);

            Orders savedOrder = Orders.createOrder(member, requestDto.getTotalPrice(), requestDto.getAddress());
            given(orderRepository.save(any(Orders.class))).willReturn(savedOrder);

            // when
            orderService.createOrder(memberId);

            // then
            verify(orderRepository, times(1)).save(any(Orders.class));
            // 결제 완료 후 Redis 임시 주문 삭제 검증
            verify(redisTemplate, times(1)).delete("tempOrder:" + memberId);
        }

        @Test
        @DisplayName("Redis에 임시 주문이 없으면 예외 발생")
        void createOrder_noTempOrder_throwsException() {
            // given
            Long memberId = 1L;
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("tempOrder:" + memberId)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> orderService.createOrder(memberId))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    // ========================================================
    // 주문 조회
    // ========================================================
    @Nested
    @DisplayName("주문 조회")
    class GetOrder {

        @Test
        @DisplayName("주문 조회 시 본인의 주문만 반환된다")
        void getOrders_returnsOnlyMyOrders() {
            // given
            Long memberId = 1L;
            Member member = createMember(memberId);

            Orders order1 = Orders.createOrder(member, BigDecimal.valueOf(30000), "서울");
            Orders order2 = Orders.createOrder(member, BigDecimal.valueOf(50000), "부산");

            given(orderRepository.findAllByMember_Id(memberId)).willReturn(List.of(order1, order2));

            // when
            var result = orderService.getOrdersByMemberId(memberId);

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("존재하지 않는 주문 조회 시 예외 발생")
        void getOrderById_notFound_throwsException() {
            given(orderRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderById(999L))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    // ========================================================
    // 헬퍼 메서드
    // ========================================================
    private Member createMember(Long memberId) {
        return Member.registerGeneral(
                "test@example.com", "encodedPw",
                "홍길동", "길동이", "010-1234-5678", "uuid"
        );
    }

    private OrderRequestDto createOrderRequest(Long memberId, BigDecimal totalPrice) {
        return OrderRequestDto.builder()
                .memberId(memberId)
                .totalPrice(totalPrice)
                .address("서울시 강남구")
                .ordererName("홍길동")
                .phoneNumber("010-1234-5678")
                .build();
    }
}
