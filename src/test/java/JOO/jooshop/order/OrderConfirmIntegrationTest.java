package JOO.jooshop.order;

import JOO.jooshop.cart.entity.Cart;
import JOO.jooshop.cart.repository.CartRepository;
import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.categorys.repository.CategoryRepository;
import JOO.jooshop.global.authentication.jwts.entity.CustomUserDetails;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.entity.enums.MemberRole;
import JOO.jooshop.members.repository.MemberRepository;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.entity.enums.PayMethod;
import JOO.jooshop.order.model.OrderDto;
import JOO.jooshop.order.repository.OrderRepository;
import JOO.jooshop.order.service.OrderService;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.ProductColor;
import JOO.jooshop.product.entity.enums.Gender;
import JOO.jooshop.product.entity.enums.ProductType;
import JOO.jooshop.product.repository.ProductColorRepository;
import JOO.jooshop.product.repository.ProductRepository;
import JOO.jooshop.productVariant.entity.ProductVariant;
import JOO.jooshop.productVariant.entity.enums.Size;
import JOO.jooshop.productVariant.repository.ProductVariantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 주문 확정 흐름 통합 테스트 — 실제 OrderService + 실제 Repository + 인메모리 H2.
 * (단위 테스트는 Repository를 가짜로 대체해서 "롤백이 실제로 되는지"는 검증할 수 없다)
 *
 * 검증 포인트:
 * 1. 주문이 확정되면 주문이 저장되고 재고가 주문 수량만큼 줄어든다
 * 2. 한 주문의 옵션 중 하나라도 재고가 부족하면 예외가 나고,
 *    이미 차감된 다른 옵션의 재고까지 포함해 전부 원래대로 돌아가며 주문도 저장되지 않는다 (원자성)
 *
 * 서비스 호출마다 별도 트랜잭션이 커밋/롤백되어야 해서 테스트 전체 트랜잭션은 끈다(NOT_SUPPORTED).
 */
@DataJpaTest
@AutoConfigureTestDatabase
@Import(OrderService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderConfirmIntegrationTest {

    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired CartRepository cartRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductColorRepository colorRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired PlatformTransactionManager txManager;

    @MockBean MemberAccountService memberAccountService; // 회원 조회만 필요 — 저장해 둔 실제 Member를 돌려준다

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("주문이 확정되면 주문이 저장되고 재고가 주문 수량만큼 줄어든다")
    void confirmOrder_success_savesOrder_and_decreasesStock() {
        Member member = saveMember("ok");
        Long variantId = createVariant("OK_A", 10);
        Cart cart = saveCart(member, variantId, 3);

        authenticateAs(member);
        Orders order = orderService.confirmOrder(orderDto(member, cart));

        assertThat(order.getOrderId()).isNotNull();
        assertThat(orderRepository.findById(order.getOrderId())).isPresent();
        assertThat(stockOf(variantId)).isEqualTo(7);
    }

    @Test
    @DisplayName("한 옵션이라도 재고가 부족하면 예외가 나고, 다른 옵션 재고와 주문 저장까지 전부 원복된다")
    void confirmOrder_insufficientStock_rollsBackEverything() {
        Member member = saveMember("fail");
        Long enoughId = createVariant("FAIL_A", 10);   // 재고 충분 (2개 주문)
        Long shortId = createVariant("FAIL_B", 1);     // 재고 부족 (5개 주문)
        // 옵션 ID 오름차순으로 처리되므로 enough → short 순서로 차감 시도: 앞 옵션은 이미 차감된 뒤에 뒤 옵션에서 실패
        Cart c1 = saveCart(member, enoughId, 2);
        Cart c2 = saveCart(member, shortId, 5);

        long ordersBefore = orderRepository.count();

        authenticateAs(member);
        assertThatThrownBy(() -> orderService.confirmOrder(orderDto(member, c1, c2)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(stockOf(enoughId)).isEqualTo(10);          // 앞서 차감된 재고도 원래대로
        assertThat(stockOf(shortId)).isEqualTo(1);
        assertThat(orderRepository.count()).isEqualTo(ordersBefore); // 주문이 남지 않음
   }

    // ───────────── 헬퍼 ─────────────

    // 회원 저장 + 서비스 mock이 그 회원을 돌려주도록 설정
    private Member saveMember(String prefix) {
        Member member = memberRepository.save(Member.registerGeneral(
                prefix + "@example.com", "encodedPw", "홍길동", prefix + "nick", "010-0000-" + Math.abs(prefix.hashCode() % 10000), prefix + "-uuid"));
        given(memberAccountService.findMemberById(member.getId())).willReturn(member);
        return member;
    }

    // 재고 stock개인 옵션을 만들고 옵션 ID 반환 (이름 접두어로 테스트 간 데이터 분리)
    private Long createVariant(String prefix, long stock) {
        ProductColor color = colorRepository.save(ProductColor.ofName(prefix + "_COLOR"));
        Category category = categoryRepository.save(Category.ofName(prefix + "_CATEGORY"));
        Product product = Product.createDemo(prefix + " 주문 테스트 상품", ProductType.values()[0],
                BigDecimal.valueOf(10_000), "info", "maker", false, 0, false);
        product.addOption(color, category, Gender.values()[0], Size.values()[0], stock);
        return productRepository.save(product).optionsView().get(0).getVariantId();
    }

    // 장바구니 저장 — Cart 생성 시 옵션의 상품 가격(지연 로딩)을 읽으므로 트랜잭션 안에서 만든다
    private Cart saveCart(Member member, Long variantId, int quantity) {
        return new TransactionTemplate(txManager).execute(status ->
                cartRepository.save(Cart.createCart(member, variantRepository.findById(variantId).orElseThrow(), quantity)));
    }

    private long stockOf(Long variantId) {
        return variantRepository.findById(variantId).map(ProductVariant::getProductStock).orElseThrow();
    }

    private OrderDto orderDto(Member member, Cart... carts) {
        return OrderDto.builder()
                .memberId(member.getId())
                .cartIds(java.util.Arrays.stream(carts).map(Cart::getCartId).toList())
                .postCode("12345").address("서울시 강남구").username("홍길동")
                .payMethod(PayMethod.card)
                .build();
    }

    // SecurityContext에 로그인 사용자 주입 (OrderService가 본인 주문인지 검증함)
    private void authenticateAs(Member member) {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        given(userDetails.getMemberId()).willReturn(member.getId());
        given(userDetails.getMemberRole()).willReturn(MemberRole.USER);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, List.of()));
        SecurityContextHolder.setContext(context);
    }
}
