package JOO.jooshop.order;

import JOO.jooshop.cart.model.CartDto;
import JOO.jooshop.order.model.OrderConfirmResponse;
import JOO.jooshop.order.model.OrderDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 화면(JS)과 서버(JSON)가 서로 약속한 필드 이름을 지키는지 확인하는 계약 테스트.
 *
 * 배경: 서버는 장바구니 응답에 "cartId"를 내려주는데 cart.html은 "item.id"를 읽고 있었다.
 * 서버 테스트는 모두 통과했지만 화면에서 주문이 실패했다. 이런 이름 불일치를 테스트로 막는다.
 * (브라우저를 띄우는 E2E 테스트 없이도 확인할 수 있는 가벼운 방법)
 */
class FrontendApiContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    @Test
    @DisplayName("장바구니 응답 JSON은 cartId 필드를 내려주고, id 필드는 없다")
    void cartDtoJson_hasCartIdNotId() throws Exception {
        CartDto dto = new CartDto(7L, 1L, "홍길동", "010-1234-5678", "상품", "M",
                BigDecimal.valueOf(10_000), 0, BigDecimal.valueOf(10_000), 2,
                BigDecimal.valueOf(20_000), "/img/a.png");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(dto));

        assertThat(json.has("cartId")).isTrue();
        assertThat(json.get("cartId").asLong()).isEqualTo(7L);
        assertThat(json.has("id")).isFalse();
    }

    @Test
    @DisplayName("cart.html은 장바구니 응답의 cartId를 읽는다 (존재하지 않는 item.id를 읽지 않는다)")
    void cartHtml_readsCartId() throws IOException {
        String html = read("carts/cart.html");

        assertThat(html).contains("item.cartId");
        assertThat(html).doesNotContain("item.id");
    }

    @Test
    @DisplayName("주문 생성 응답 JSON은 화면이 쓰는 orderId, merchantUid, amount, orderName을 내려준다")
    void orderConfirmResponseJson_hasFieldsUsedByOrderHtml() throws Exception {
        OrderConfirmResponse response = new OrderConfirmResponse(15L, "20261025-abcd1234",
                BigDecimal.valueOf(35_000), "테스트 상품");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.has("orderId")).isTrue();
        assertThat(json.has("merchantUid")).isTrue();
        assertThat(json.has("amount")).isTrue();
        assertThat(json.has("orderName")).isTrue();

        String orderHtml = read("orders/order.html");
        assertThat(orderHtml).contains("serverOrder.orderId", "serverOrder.merchantUid", "serverOrder.amount");
    }

    @Test
    @DisplayName("화면이 보내는 장바구니 주문 JSON(cartIds)이 OrderDto로 읽힌다")
    void cartOrderRequestJson_isReadableAsOrderDto() throws Exception {
        String body = """
                {"memberId":1,"cartIds":[10,11],"postCode":"12345","address":"서울","username":"홍길동","payMethod":"card"}
                """;

        OrderDto dto = objectMapper.readValue(body, OrderDto.class);

        assertThat(dto.getCartIds()).containsExactly(10L, 11L);
        assertThat(dto.getItems()).isNull();
    }

    @Test
    @DisplayName("화면이 보내는 바로 구매 JSON(items)이 OrderDto로 읽힌다")
    void buyNowRequestJson_isReadableAsOrderDto() throws Exception {
        String body = """
                {"memberId":1,"items":[{"variantId":7,"quantity":2}],"postCode":"12345","address":"서울","username":"홍길동","payMethod":"card"}
                """;

        OrderDto dto = objectMapper.readValue(body, OrderDto.class);

        assertThat(dto.getItems()).hasSize(1);
        assertThat(dto.getItems().get(0).getVariantId()).isEqualTo(7L);
        assertThat(dto.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(TEMPLATES.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
