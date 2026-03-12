package com.loopers.application.order;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeIntegrationTest {

    @Autowired private OrderFacade orderFacade;
    @Autowired private OrderService orderService;
    @Autowired private ProductFacade productFacade;
    @Autowired private ProductService productService;
    @Autowired private BrandService brandService;
    @Autowired private StockService stockService;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    private Long createBrand(String name) { return brandService.register(name, "설명").getId(); }
    private Long createProduct(String name, int price, Long brandId, int stock) {
        return productFacade.register(name, "설명", new Money(price), brandId, stock).getId();
    }

    @DisplayName("주문 생성")
    @Nested
    class PlaceOrder {

        @DisplayName("단일 상품 주문이 성공한다")
        @Test
        void placesSingleItemOrder() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId, 10);
            OrderResult result = orderFacade.placeOrder(1L, List.of(new OrderItemCommand(productId, 2)));
            assertAll(
                () -> assertThat(result.order().userId()).isEqualTo(1L),
                () -> assertThat(result.order().status()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(result.order().totalAmount()).isEqualTo(new Money(258000)),
                () -> assertThat(result.items()).hasSize(1),
                () -> assertThat(result.items().get(0).productName()).isEqualTo("에어맥스"),
                () -> assertThat(result.items().get(0).quantity()).isEqualTo(2)
            );
        }

        @DisplayName("복수 상품 주문이 성공한다")
        @Test
        void placesMultiItemOrder() {
            Long brandId = createBrand("나이키");
            Long p1 = createProduct("에어맥스", 129000, brandId, 10);
            Long p2 = createProduct("조던", 159000, brandId, 5);
            OrderResult result = orderFacade.placeOrder(1L, List.of(
                new OrderItemCommand(p1, 2), new OrderItemCommand(p2, 1)));
            assertAll(
                () -> assertThat(result.order().totalAmount()).isEqualTo(new Money(417000)),
                () -> assertThat(result.items()).hasSize(2)
            );
        }

        @DisplayName("주문 시 재고가 차감된다")
        @Test
        void deductsStock() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId, 10);
            orderFacade.placeOrder(1L, List.of(new OrderItemCommand(productId, 3)));
            assertThat(stockService.getByProductId(productId).quantity()).isEqualTo(7);
        }

        @DisplayName("재고 부족 시 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsWhenInsufficientStock() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId, 2);
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(1L, List.of(new OrderItemCommand(productId, 5))));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("두 번째 상품 재고 부족 시 첫 번째 상품 재고도 롤백된다")
        @Test
        void rollsBackOnPartialFailure() {
            Long brandId = createBrand("나이키");
            Long p1 = createProduct("에어맥스", 129000, brandId, 10);
            Long p2 = createProduct("조던", 159000, brandId, 1);
            assertThrows(CoreException.class, () -> orderFacade.placeOrder(1L, List.of(
                new OrderItemCommand(p1, 3), new OrderItemCommand(p2, 5))));
            assertThat(stockService.getByProductId(p1).quantity()).isEqualTo(10);
            assertThat(stockService.getByProductId(p2).quantity()).isEqualTo(1);
        }

        @DisplayName("삭제된 상품 주문 시 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsWhenProductDeleted() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId, 10);
            productService.delete(productId);
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(1L, List.of(new OrderItemCommand(productId, 1))));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("스냅샷으로 주문 당시 상품 정보가 저장된다")
        @Test
        void savesProductSnapshot() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId, 10);
            OrderResult result = orderFacade.placeOrder(1L, List.of(new OrderItemCommand(productId, 1)));
            OrderItemModel item = result.items().get(0);
            assertAll(
                () -> assertThat(item.productName()).isEqualTo("에어맥스"),
                () -> assertThat(item.productPrice()).isEqualTo(new Money(129000))
            );
        }
    }

    @DisplayName("주문 조회")
    @Nested
    class GetOrder {

        @DisplayName("본인의 주문을 조회할 수 있다")
        @Test
        void getsOwnOrder() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId, 10);
            OrderResult r = orderFacade.placeOrder(1L, List.of(new OrderItemCommand(productId, 1)));
            OrderModel order = orderService.getOrder(r.order().getId(), 1L);
            assertThat(order.getId()).isEqualTo(r.order().getId());
        }

        @DisplayName("다른 사용자의 주문을 조회하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsWhenAccessingOtherUserOrder() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId, 10);
            OrderResult r = orderFacade.placeOrder(1L, List.of(new OrderItemCommand(productId, 1)));
            CoreException result = assertThrows(CoreException.class,
                () -> orderService.getOrder(r.order().getId(), 999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 상품 목록을 조회할 수 있다")
        @Test
        void getsOrderItems() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId, 10);
            OrderResult r = orderFacade.placeOrder(1L, List.of(new OrderItemCommand(productId, 2)));
            List<OrderItemModel> items = orderService.getOrderItems(r.order().getId());
            assertAll(
                () -> assertThat(items).hasSize(1),
                () -> assertThat(items.get(0).productName()).isEqualTo("에어맥스"),
                () -> assertThat(items.get(0).quantity()).isEqualTo(2)
            );
        }
    }
}
