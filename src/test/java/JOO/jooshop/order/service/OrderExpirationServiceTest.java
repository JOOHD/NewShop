package JOO.jooshop.order.service;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.order.entity.OrderProduct;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.entity.enums.PayMethod;
import JOO.jooshop.order.repository.OrderRepository;
import JOO.jooshop.payment.entity.PaymentStatus;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.productVariant.entity.ProductVariant;
import JOO.jooshop.productVariant.repository.ProductVariantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 결제 대기 주문 만료 처리 테스트.
 * - 결제되지 않은(PENDING) 주문은 취소되고 차감했던 재고가 같은 옵션끼리 합산되어 복구된다.
 * - 이미 결제된 주문, 없는 주문은 건드리지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock ProductVariantRepository productVariantRepository;
    @InjectMocks OrderExpirationService orderExpirationService;

    @Test
    @DisplayName("결제 대기 주문을 만료하면 주문이 취소되고, 같은 옵션의 수량은 합산해 재고를 복구한다")
    void expire_pendingOrder_cancelsAndRestoresStock() {
        // given — 10번 옵션 1개 + 2개(같은 옵션), 20번 옵션 4개
        Orders order = createOrder();
        order.addOrderProduct(orderProduct(10L, 1));
        order.addOrderProduct(orderProduct(10L, 2));
        order.addOrderProduct(orderProduct(20L, 4));
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        boolean expired = orderExpirationService.expire(1L);

        // then
        assertThat(expired).isTrue();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        verify(productVariantRepository).increaseStock(10L, 3L);  // 1 + 2 합산
        verify(productVariantRepository).increaseStock(20L, 4L);
    }

    @Test
    @DisplayName("이미 결제된 주문은 만료 처리하지 않고 재고도 건드리지 않는다")
    void expire_paidOrder_isIgnored() {
        // given — 목록 조회 이후 결제가 완료된 주문
        Orders order = createOrder();
        order.addOrderProduct(orderProduct(10L, 1));
        order.markPaid();
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        boolean expired = orderExpirationService.expire(1L);

        // then
        assertThat(expired).isFalse();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETE);
        verify(productVariantRepository, never()).increaseStock(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("존재하지 않는 주문은 아무 일도 하지 않는다")
    void expire_missingOrder_isIgnored() {
        given(orderRepository.findByIdForUpdate(999L)).willReturn(Optional.empty());

        assertThat(orderExpirationService.expire(999L)).isFalse();
        verify(productVariantRepository, never()).increaseStock(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    // ---------- 헬퍼 ----------

    private Orders createOrder() {
        Member member = Member.registerGeneral(
                "test@example.com", "encodedPw", "홍길동", "길동이", "010-1234-5678", "uuid");
        return Orders.createOrder(member, "홍길동", "010-1234-5678", "12345",
                "서울시 강남구", null, PayMethod.card, "order_test");
    }

    private OrderProduct orderProduct(Long variantId, int quantity) {
        ProductVariant variant = mock(ProductVariant.class);
        lenient().when(variant.getVariantId()).thenReturn(variantId);
        lenient().when(variant.getProduct()).thenReturn(Product.ofId(1L));
        return OrderProduct.createOrderProduct(variant, "테스트 상품", "M", null, BigDecimal.valueOf(10_000), quantity);
    }
}
