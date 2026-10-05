package JOO.jooshop.global.demo;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.repository.MemberRepository;
import JOO.jooshop.order.entity.OrderProduct;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.entity.enums.PayMethod;
import JOO.jooshop.order.repository.OrderRepository;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.repository.ProductRepository;
import JOO.jooshop.productVariant.entity.ProductVariant;
import JOO.jooshop.productVariant.entity.enums.Size;
import JOO.jooshop.productVariant.repository.ProductVariantRepository;
import JOO.jooshop.wishList.entity.WishList;
import JOO.jooshop.wishList.repository.WishListRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 로컬 개발용 데모 주문/좋아요 자동 생성 (스크린샷, 마이페이지/주문내역 화면 확인용).
 *
 * DemoAccountInitializer(계정, Order 0)와 DemoProductInitializer(상품, Order 1)가 먼저 실행된 뒤
 * 그 데이터를 이용해 user@jooshop.com 의 주문 4건과 좋아요 4건을 만든다.
 * ddl-auto가 create-drop일 때(로컬)만 등록되고, 이미 주문이 있으면 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(3)
@ConditionalOnProperty(name = "spring.jpa.hibernate.ddl-auto", havingValue = "create-drop")
public class DemoOrderInitializer implements CommandLineRunner {

    private static final String USER_EMAIL = "user@jooshop.com";
    private static final String P_STONE_JERSEY = "Manchester United x adidas Stone Roses Jersey Blue";
    private static final String P_STONE_TSHIRT = "Manchester United x adidas Stone Roses T-Shirt White";
    private static final String P_EQT_JERSEY = "Manchester United x adidas EQT Jersey Red";
    private static final String P_EQT_HALFZIP = "Manchester United x adidas EQT Half Zip Top Black";

    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderRepository orderRepository;
    private final WishListRepository wishListRepository;
    private final EntityManager entityManager;

    // 주문 한 줄(상품명, 사이즈, 수량)
    private record Item(String productName, Size size, int quantity) {}

    // 앱 시작 시 데모 주문/좋아요 생성
    @Override
    @Transactional
    public void run(String... args) {
        Member user = memberRepository.findByEmail(USER_EMAIL).orElse(null);
        if (user == null) {
            return; // 계정이 없으면 건너뜀
        }

        createOrders(user);
        createWishList(user);
    }

    // 주문 4건 생성 — 단일 상품 / 수량 2개 / 한 주문에 상품 2종 / 계좌이체 케이스
    private void createOrders(Member user) {
        if (!orderRepository.findAllByMemberIdOrderByOrderDayDesc(user.getId()).isEmpty()) {
            return;
        }

        createOrder(user, "demo-order-0001", PayMethod.card, 1, new Item(P_STONE_JERSEY, Size.L, 1));
        createOrder(user, "demo-order-0002", PayMethod.card, 3, new Item(P_STONE_TSHIRT, Size.L, 2));
        createOrder(user, "demo-order-0003", PayMethod.card, 7,
                new Item(P_EQT_JERSEY, Size.M, 1), new Item(P_STONE_JERSEY, Size.L, 1));
        createOrder(user, "demo-order-0004", PayMethod.trans, 12, new Item(P_EQT_HALFZIP, Size.XL, 1));
    }

    // 주문 1건 생성 후 주문일을 daysAgo일 전으로 조정 (주문일은 생성 시각으로 자동 기록되어 별도 UPDATE 필요)
    private void createOrder(Member user, String merchantUid, PayMethod payMethod, int daysAgo, Item... items) {
        Orders order = Orders.createOrder(
                user, user.getNickname(), "01012345678",
                "06234", "서울특별시 강남구 테헤란로 123", "101동 1001호",
                payMethod, merchantUid);

        for (Item item : items) {
            List<ProductVariant> variants = productVariantRepository.findByProductNameAndSize(item.productName(), item.size());
            if (variants.isEmpty()) {
                log.warn("[DemoOrder] variant not found: {} / {}", item.productName(), item.size());
                return; // 상품이 없으면 이 주문은 만들지 않음
            }

            ProductVariant variant = variants.get(0);
            Product product = variant.getProduct();
            String image = product.getProductThumbnails().isEmpty()
                    ? null
                    : product.getProductThumbnails().get(0).getImagesPath();

            order.addOrderProduct(OrderProduct.createOrderProduct(
                    variant, product.getProductName(), item.size().name(), image, product.getPrice(), item.quantity()));
        }

        Orders saved = orderRepository.saveAndFlush(order);

        entityManager.createNativeQuery("update orders set created_at = ?1 where order_id = ?2")
                .setParameter(1, LocalDateTime.now().minusDays(daysAgo))
                .setParameter(2, saved.getOrderId())
                .executeUpdate();
    }

    // 좋아요 — 등록 순서상 앞쪽 상품 4개
    private void createWishList(Member user) {
        if (!wishListRepository.findAllByMemberId(user.getId()).isEmpty()) {
            return;
        }

        List<Product> products = productRepository
                .findAll(PageRequest.of(0, 4, Sort.by("productId"))).getContent();
        products.forEach(p -> wishListRepository.save(new WishList(user, p)));

        log.info("[DemoOrder] created demo orders and {} wishes for {}", products.size(), USER_EMAIL);
    }
}
