package com.loopers.application.payment;

import com.loopers.application.payment.dto.CreatePaymentReqDto;
import com.loopers.application.payment.dto.FindPaymentResDto;
import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.model.BrandCommand;
import com.loopers.domain.member.model.MemberCommand;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.model.OrderCommand;
import com.loopers.domain.order.model.OrderProduct;
import com.loopers.domain.order.model.Orders;
import com.loopers.domain.order.service.OrderService;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentInfo;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgPaymentStatus;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductCommand;
import com.loopers.domain.product.service.ProductService;
import com.loopers.infrastructure.brand.entity.BrandEntity;
import com.loopers.infrastructure.brand.repository.BrandJpaRepository;
import com.loopers.infrastructure.member.entity.MemberEntity;
import com.loopers.infrastructure.member.repository.MemberJpaRepository;
import com.loopers.infrastructure.order.repository.OrderJpaRepository;
import com.loopers.infrastructure.order.repository.OrderProductJpaRepository;
import com.loopers.infrastructure.payment.repository.PaymentJpaRepository;
import com.loopers.infrastructure.payment.repository.PaymentProductJpaRepository;
import com.loopers.infrastructure.product.entity.ProductEntity;
import com.loopers.infrastructure.product.repository.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.profiles.active=test")
@Import(MySqlTestContainersConfig.class)
class PaymentFacadeIntegrationTest {

    @Autowired
    private PaymentFacade paymentFacade;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private MemberService memberService;

    @MockBean
    private PaymentGateway paymentGateway;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private PaymentProductJpaRepository paymentProductJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OrderProductJpaRepository orderProductJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private MemberJpaRepository memberJpaRepository;

    @BeforeEach
    void setUp() {
        paymentProductJpaRepository.deleteAll();
        paymentJpaRepository.deleteAll();
        orderProductJpaRepository.deleteAll();
        orderJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
        brandJpaRepository.deleteAll();
        memberJpaRepository.deleteAll();
    }

    private MemberEntity saveMember(String loginId) {
        memberService.addMember(new MemberCommand.SignUp(
                loginId, "Password123!", "홍길동",
                LocalDate.of(1990, 1, 15), "test@example.com"
        ));
        return memberJpaRepository.findByLoginId(loginId).orElseThrow();
    }

    private BrandEntity saveBrand() {
        Brand brand = Brand.create(new BrandCommand.Create("테스트브랜드", "테스트 설명"));
        return brandJpaRepository.save(BrandEntity.toEntity(brand));
    }

    private ProductEntity saveProduct(Long brandId, String name, int price, int stock) {
        Product product = Product.create(brandId, new ProductCommand.Create(brandId, name, price, stock));
        return productJpaRepository.save(ProductEntity.toEntity(product));
    }

    private Orders createOrder(MemberEntity member, ProductEntity product, int quantity) {
        List<OrderProduct> orderProducts = productService.decreaseStockAndCreateOrderProducts(
                List.of(new OrderCommand.OrderItem(product.getId(), quantity)));
        int subtotal = orderProducts.stream().mapToInt(OrderProduct::subtotal).sum();
        OrderCommand.Create command = new OrderCommand.Create(member.getId(), orderProducts, 0, null);
        return orderService.createOrder(command);
    }

    @DisplayName("결제 생성")
    @Nested
    class CreatePayment {

        @DisplayName("정상 결제 생성 시 Payment가 PENDING 상태로 저장된다")
        @Test
        void createPayment_success() {
            // arrange
            MemberEntity member = saveMember("testuser");
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);
            Orders order = createOrder(member, product, 2);

            CreatePaymentReqDto dto = new CreatePaymentReqDto(
                    order.getOrderNumber(), "VISA", "1234-5678-9012-3456");

            // act
            FindPaymentResDto result = paymentFacade.createPayment("testuser", "Password123!", dto);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo(PaymentStatus.PENDING.name());
        }

        @DisplayName("다른 사람의 주문으로 결제 시도 시 NOT_FOUND 예외가 발생한다")
        @Test
        void createPayment_ownershipFail() {
            // arrange
            MemberEntity owner = saveMember("owner");
            MemberEntity other = saveMember("other");
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);
            Orders order = createOrder(owner, product, 1);

            CreatePaymentReqDto dto = new CreatePaymentReqDto(
                    order.getOrderNumber(), "VISA", "1234-5678-9012-3456");

            // act & assert
            assertThatThrownBy(() -> paymentFacade.createPayment("other", "Password123!", dto))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @DisplayName("결제 콜백")
    @Nested
    class HandleCallback {

        @DisplayName("SUCCESS 콜백 시 결제 성공 + 주문 PAID 처리된다")
        @Test
        void handleCallback_success() {
            // arrange
            MemberEntity member = saveMember("testuser");
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);
            Orders order = createOrder(member, product, 2);

            CreatePaymentReqDto dto = new CreatePaymentReqDto(
                    order.getOrderNumber(), "VISA", "1234-5678-9012-3456");
            paymentFacade.createPayment("testuser", "Password123!", dto);

            // Payment를 REQUESTED 상태로 변경 (PG 호출 성공 시뮬레이션)
            var paymentEntity = paymentJpaRepository.findByOrderId(order.getOrderNumber()).orElseThrow();
            paymentJpaRepository.updateTransactionKeyAndStatus(
                    paymentEntity.getId(), "txn-123", PaymentStatus.REQUESTED);

            // act
            paymentFacade.handleCallback("txn-123", PgPaymentStatus.SUCCESS);

            // assert
            var updatedPayment = paymentJpaRepository.findByTransactionKey("txn-123").orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

            var updatedOrder = orderJpaRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @DisplayName("FAILED 콜백 시 결제 실패 + 주문 PAYMENT_FAILED + 재고 복원된다")
        @Test
        void handleCallback_failed() {
            // arrange
            MemberEntity member = saveMember("testuser");
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);
            int orderQuantity = 2;
            Orders order = createOrder(member, product, orderQuantity);

            CreatePaymentReqDto dto = new CreatePaymentReqDto(
                    order.getOrderNumber(), "VISA", "1234-5678-9012-3456");
            paymentFacade.createPayment("testuser", "Password123!", dto);

            var paymentEntity = paymentJpaRepository.findByOrderId(order.getOrderNumber()).orElseThrow();
            paymentJpaRepository.updateTransactionKeyAndStatus(
                    paymentEntity.getId(), "txn-456", PaymentStatus.REQUESTED);

            // act
            paymentFacade.handleCallback("txn-456", PgPaymentStatus.FAILED);

            // assert
            var updatedPayment = paymentJpaRepository.findByTransactionKey("txn-456").orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);

            var updatedOrder = orderJpaRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);

            // 재고 복원 확인
            var updatedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updatedProduct.getStock()).isEqualTo(100); // 원래 재고로 복원
        }
    }

    @DisplayName("결제 상태 확인")
    @Nested
    class CheckPaymentStatus {

        @DisplayName("소유자 검증 실패 시 NOT_FOUND 예외가 발생한다")
        @Test
        void checkPaymentStatus_ownershipFail() {
            // arrange
            MemberEntity owner = saveMember("owner");
            MemberEntity other = saveMember("other");
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);
            Orders order = createOrder(owner, product, 1);

            CreatePaymentReqDto dto = new CreatePaymentReqDto(
                    order.getOrderNumber(), "VISA", "1234-5678-9012-3456");
            paymentFacade.createPayment("owner", "Password123!", dto);

            // act & assert
            assertThatThrownBy(() -> paymentFacade.checkPaymentStatus("other", "Password123!", order.getOrderNumber()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("PG 조회 결과가 SUCCESS이면 결제 완료 처리된다")
        @Test
        void checkPaymentStatus_pgSuccess() {
            // arrange
            MemberEntity member = saveMember("testuser");
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);
            Orders order = createOrder(member, product, 1);

            CreatePaymentReqDto dto = new CreatePaymentReqDto(
                    order.getOrderNumber(), "VISA", "1234-5678-9012-3456");
            paymentFacade.createPayment("testuser", "Password123!", dto);

            var paymentEntity = paymentJpaRepository.findByOrderId(order.getOrderNumber()).orElseThrow();
            paymentJpaRepository.updateTransactionKeyAndStatus(
                    paymentEntity.getId(), "txn-789", PaymentStatus.REQUESTED);

            when(paymentGateway.getPaymentByOrderId(eq(member.getId()), eq(order.getOrderNumber())))
                    .thenReturn(new PaymentInfo("txn-789", order.getOrderNumber(), "VISA", "1234", "10000", PgPaymentStatus.SUCCESS));

            // act
            FindPaymentResDto result = paymentFacade.checkPaymentStatus("testuser", "Password123!", order.getOrderNumber());

            // assert
            assertThat(result).isNotNull();

            var updatedOrder = orderJpaRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @DisplayName("PG 조회 결과가 PENDING이면 상태 변경 없이 반환한다")
        @Test
        void checkPaymentStatus_pgPending() {
            // arrange
            MemberEntity member = saveMember("testuser");
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);
            Orders order = createOrder(member, product, 1);

            CreatePaymentReqDto dto = new CreatePaymentReqDto(
                    order.getOrderNumber(), "VISA", "1234-5678-9012-3456");
            paymentFacade.createPayment("testuser", "Password123!", dto);

            var paymentEntity = paymentJpaRepository.findByOrderId(order.getOrderNumber()).orElseThrow();
            paymentJpaRepository.updateTransactionKeyAndStatus(
                    paymentEntity.getId(), "txn-000", PaymentStatus.REQUESTED);

            when(paymentGateway.getPaymentByOrderId(eq(member.getId()), eq(order.getOrderNumber())))
                    .thenReturn(new PaymentInfo("txn-000", order.getOrderNumber(), "VISA", "1234", "10000", PgPaymentStatus.PENDING));

            // act
            FindPaymentResDto result = paymentFacade.checkPaymentStatus("testuser", "Password123!", order.getOrderNumber());

            // assert
            assertThat(result).isNotNull();

            var updatedOrder = orderJpaRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CREATED); // 변경 없음
        }
    }
}
