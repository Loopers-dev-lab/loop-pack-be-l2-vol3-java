package com.loopers.domain.order;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.model.BrandCommand;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.model.MemberCommand;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.order.model.OrderCommand;
import com.loopers.domain.order.model.OrderProduct;
import com.loopers.domain.order.model.Orders;
import com.loopers.domain.order.service.OrderProductService;
import com.loopers.domain.order.service.OrderService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductCommand;
import com.loopers.infrastructure.brand.entity.BrandEntity;
import com.loopers.infrastructure.brand.repository.BrandJpaRepository;
import com.loopers.infrastructure.member.entity.MemberEntity;
import com.loopers.infrastructure.member.repository.MemberJpaRepository;
import com.loopers.infrastructure.order.repository.OrderJpaRepository;
import com.loopers.infrastructure.order.repository.OrderProductJpaRepository;
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
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(MySqlTestContainersConfig.class)
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderProductService orderProductService;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OrderProductJpaRepository orderProductJpaRepository;

    @Autowired
    private MemberJpaRepository memberJpaRepository;

    @Autowired
    private MemberService memberService;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @BeforeEach
    void setUp() {
        orderProductJpaRepository.deleteAll();
        orderJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
        brandJpaRepository.deleteAll();
        memberJpaRepository.deleteAll();
    }

    private MemberEntity saveMember() {
        memberService.addMember(new MemberCommand.SignUp(
            "testuser", "Password123!", "홍길동",
            LocalDate.of(1990, 1, 15), "test@example.com"
        ));
        return memberJpaRepository.findByLoginId("testuser").orElseThrow();
    }

    private BrandEntity saveBrand() {
        Brand brand = Brand.create(new BrandCommand.Create("테스트브랜드", "테스트 설명"));
        return brandJpaRepository.save(BrandEntity.toEntity(brand));
    }

    private ProductEntity saveProduct(Long brandId, String name, int price, int stock) {
        Product product = Product.create(brandId, new ProductCommand.Create(brandId, name, price, stock));
        return productJpaRepository.save(ProductEntity.toEntity(product));
    }

    @DisplayName("주문 생성")
    @Nested
    class CreateOrder {

        @DisplayName("정상적인 주문을 생성하면 DB에 저장되고 총 가격이 계산된다")
        @Test
        void createOrder_success() {
            // arrange
            MemberEntity member = saveMember();
            BrandEntity brand = saveBrand();
            ProductEntity product1 = saveProduct(brand.getId(), "상품A", 10000, 100);
            ProductEntity product2 = saveProduct(brand.getId(), "상품B", 20000, 50);

            List<OrderProduct> orderProducts = List.of(
                OrderProduct.create(product1.getId(), "상품A", 10000, 2),
                OrderProduct.create(product2.getId(), "상품B", 20000, 1)
            );
            OrderCommand.Create command = new OrderCommand.Create(member.getId(), orderProducts, 0, null);

            // act
            Orders result = orderService.createOrder(command);

            // assert
            assertThat(result.getId()).isNotNull();
            assertThat(result.getTotalPrice().value()).isEqualTo(40000); // 10000*2 + 20000*1
            assertThat(result.getMemberId()).isEqualTo(member.getId());

            // DB 저장 확인
            assertThat(orderJpaRepository.findById(result.getId())).isPresent();
        }
    }

    @DisplayName("기간별 주문 조회")
    @Nested
    class GetOrders {

        @DisplayName("주문이 없으면 빈 결과를 반환한다")
        @Test
        void getOrders_empty() {
            // arrange
            MemberEntity member = saveMember();
            OrderCommand.GetByPeriod command = new OrderCommand.GetByPeriod(
                member.getId(),
                LocalDateTime.now().minusDays(7),
                LocalDateTime.now()
            );

            // act
            List<Orders> result = orderService.getOrders(command);

            // assert
            assertThat(result).isEmpty();
        }

        @DisplayName("기간 내 주문만 조회된다")
        @Test
        void getOrders_withinPeriod() {
            // arrange
            MemberEntity member = saveMember();
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);

            List<OrderProduct> orderProducts = List.of(
                OrderProduct.create(product.getId(), "상품A", 10000, 1)
            );
            orderService.createOrder(new OrderCommand.Create(member.getId(), orderProducts, 0, null));

            OrderCommand.GetByPeriod command = new OrderCommand.GetByPeriod(
                member.getId(),
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
            );

            // act
            List<Orders> result = orderService.getOrders(command);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMemberId()).isEqualTo(member.getId());
        }
    }

    @DisplayName("주문 단건 조회")
    @Nested
    class GetOrder {

        @DisplayName("존재하지 않는 주문을 조회하면 NOT_FOUND 예외가 발생한다")
        @Test
        void getOrder_notFound() {
            // arrange
            MemberEntity member = saveMember();
            OrderCommand.GetByMember command = new OrderCommand.GetByMember(member.getId(), 999L);

            // act & assert
            assertThatThrownBy(() -> orderService.getOrder(command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("다른 회원의 주문을 조회하면 NOT_FOUND 예외가 발생한다")
        @Test
        void getOrder_otherMember() {
            // arrange
            MemberEntity member = saveMember();
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);

            List<OrderProduct> orderProducts = List.of(
                OrderProduct.create(product.getId(), "상품A", 10000, 1)
            );
            Orders order = orderService.createOrder(new OrderCommand.Create(member.getId(), orderProducts, 0, null));

            Long otherMemberId = member.getId() + 9999L;
            OrderCommand.GetByMember command = new OrderCommand.GetByMember(otherMemberId, order.getId());

            // act & assert
            assertThatThrownBy(() -> orderService.getOrder(command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("본인의 주문을 조회하면 주문 정보를 반환한다")
        @Test
        void getOrder_success() {
            // arrange
            MemberEntity member = saveMember();
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);

            List<OrderProduct> orderProducts = List.of(
                OrderProduct.create(product.getId(), "상품A", 10000, 2)
            );
            Orders order = orderService.createOrder(new OrderCommand.Create(member.getId(), orderProducts, 0, null));

            OrderCommand.GetByMember command = new OrderCommand.GetByMember(member.getId(), order.getId());

            // act
            Orders result = orderService.getOrder(command);

            // assert
            assertThat(result.getId()).isEqualTo(order.getId());
            assertThat(result.getMemberId()).isEqualTo(member.getId());
            assertThat(result.getTotalPrice().value()).isEqualTo(20000);
        }
    }
}
