package com.loopers.application.order;

import com.loopers.application.product.ProductAppService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.Money;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("OrderFacade 통합 테스트")
class OrderFacadeIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private ProductAppService productAppService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long brandId;
    private Long productId;
    private Long optionId;

    @BeforeEach
    void setUp() {
        Brand brand = Brand.create("테스트 브랜드");
        BrandJpaEntity brandEntity = brandJpaRepository.save(BrandJpaEntity.from(brand));
        brandId = brandEntity.getId();

        Product product = productAppService.create(brandId, "테스트 상품", Money.of(10000L));
        productId = product.getId();

        Option option = productAppService.createOption(productId, "기본 옵션", Money.of(1000L), 100);
        optionId = option.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("주문 생성")
    class CreateOrderTest {

        @Test
        @DisplayName("주문 생성 시 재고가 차감된다")
        void createOrder_decreasesStock() {
            // given
            int orderQuantity = 10;
            OrderCreateCommand command = new OrderCreateCommand(
                    1L,
                    List.of(new OrderCreateCommand.OrderItemCommand(optionId, orderQuantity))
            );

            // when
            Order order = orderFacade.createOrder(command);

            // then
            assertThat(order).isNotNull();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

            Option updatedOption = productAppService.getOptionById(optionId);
            assertThat(updatedOption.getStock()).isEqualTo(90);
        }

        @Test
        @DisplayName("여러 옵션을 주문하면 각 옵션의 재고가 차감된다")
        void createOrder_multipleOptions_decreasesAllStocks() {
            // given
            Option option2 = productAppService.createOption(productId, "추가 옵션", Money.of(2000L), 50);
            Long optionId2 = option2.getId();

            OrderCreateCommand command = new OrderCreateCommand(
                    1L,
                    List.of(
                            new OrderCreateCommand.OrderItemCommand(optionId, 5),
                            new OrderCreateCommand.OrderItemCommand(optionId2, 10)
                    )
            );

            // when
            Order order = orderFacade.createOrder(command);

            // then
            assertThat(order.getOrderItems()).hasSize(2);
            assertThat(productAppService.getOptionById(optionId).getStock()).isEqualTo(95);
            assertThat(productAppService.getOptionById(optionId2).getStock()).isEqualTo(40);
        }

        @Test
        @DisplayName("재고보다 많은 수량을 주문하면 예외가 발생한다")
        void createOrder_insufficientStock_throwsException() {
            // given
            OrderCreateCommand command = new OrderCreateCommand(
                    1L,
                    List.of(new OrderCreateCommand.OrderItemCommand(optionId, 150))
            );

            // when & then
            assertThatThrownBy(() -> orderFacade.createOrder(command))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);

            Option unchangedOption = productAppService.getOptionById(optionId);
            assertThat(unchangedOption.getStock()).isEqualTo(100);
        }

        @Test
        @DisplayName("주문 금액이 올바르게 계산된다")
        void createOrder_calculatesCorrectTotalAmount() {
            // given
            OrderCreateCommand command = new OrderCreateCommand(
                    1L,
                    List.of(new OrderCreateCommand.OrderItemCommand(optionId, 3))
            );

            // when
            Order order = orderFacade.createOrder(command);

            // then
            assertThat(order.getOrderItems().get(0).getPrice().getAmount())
                    .isEqualByComparingTo("11000");
            assertThat(order.getTotalAmount().getAmount())
                    .isEqualByComparingTo("33000");
        }
    }

    @Nested
    @DisplayName("주문 취소")
    class CancelOrderTest {

        @Test
        @DisplayName("주문 취소 시 재고가 복구된다")
        void cancelOrder_restoresStock() {
            // given
            OrderCreateCommand command = new OrderCreateCommand(
                    1L,
                    List.of(new OrderCreateCommand.OrderItemCommand(optionId, 20))
            );
            Order order = orderFacade.createOrder(command);
            assertThat(productAppService.getOptionById(optionId).getStock()).isEqualTo(80);

            // when
            Order cancelledOrder = orderFacade.cancelOrder(1L, order.getId());

            // then
            assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(productAppService.getOptionById(optionId).getStock()).isEqualTo(100);
        }

        @Test
        @DisplayName("여러 옵션이 포함된 주문 취소 시 모든 재고가 복구된다")
        void cancelOrder_multipleOptions_restoresAllStocks() {
            // given
            Option option2 = productAppService.createOption(productId, "추가 옵션", Money.of(2000L), 50);
            Long optionId2 = option2.getId();

            OrderCreateCommand command = new OrderCreateCommand(
                    1L,
                    List.of(
                            new OrderCreateCommand.OrderItemCommand(optionId, 10),
                            new OrderCreateCommand.OrderItemCommand(optionId2, 20)
                    )
            );
            Order order = orderFacade.createOrder(command);

            // when
            orderFacade.cancelOrder(1L, order.getId());

            // then
            assertThat(productAppService.getOptionById(optionId).getStock()).isEqualTo(100);
            assertThat(productAppService.getOptionById(optionId2).getStock()).isEqualTo(50);
        }

        @Test
        @DisplayName("결제 완료 상태에서도 취소가 가능하다")
        void cancelOrder_paidStatus_canCancel() {
            // given
            OrderCreateCommand command = new OrderCreateCommand(
                    1L,
                    List.of(new OrderCreateCommand.OrderItemCommand(optionId, 10))
            );
            Order order = orderFacade.createOrder(command);
            orderFacade.payOrder(order.getId());

            // when
            Order cancelledOrder = orderFacade.cancelOrder(1L, order.getId());

            // then
            assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(productAppService.getOptionById(optionId).getStock()).isEqualTo(100);
        }

        @Test
        @DisplayName("준비중 상태에서는 취소가 불가능하다")
        void cancelOrder_preparingStatus_throwsException() {
            // given
            OrderCreateCommand command = new OrderCreateCommand(
                    1L,
                    List.of(new OrderCreateCommand.OrderItemCommand(optionId, 10))
            );
            Order order = orderFacade.createOrder(command);
            orderFacade.payOrder(order.getId());
            orderFacade.prepareOrder(order.getId());

            // when & then
            assertThatThrownBy(() -> orderFacade.cancelOrder(1L, order.getId()))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("주문 상태 전이")
    class OrderStatusTransitionTest {

        @Test
        @DisplayName("주문 생성 → 결제 → 준비중 → 배송중 → 배송완료 흐름이 정상 동작한다")
        void orderStatusFlow_happyPath() {
            // given
            OrderCreateCommand command = new OrderCreateCommand(
                    1L,
                    List.of(new OrderCreateCommand.OrderItemCommand(optionId, 5))
            );

            // when
            Order order = orderFacade.createOrder(command);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

            Order paidOrder = orderFacade.payOrder(order.getId());
            assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);

            Order preparingOrder = orderFacade.prepareOrder(order.getId());
            assertThat(preparingOrder.getStatus()).isEqualTo(OrderStatus.PREPARING);

            Order shippedOrder = orderFacade.shipOrder(order.getId());
            assertThat(shippedOrder.getStatus()).isEqualTo(OrderStatus.SHIPPED);

            Order deliveredOrder = orderFacade.deliverOrder(order.getId());
            assertThat(deliveredOrder.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }
    }
}
