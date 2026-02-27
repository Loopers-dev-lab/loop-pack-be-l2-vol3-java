package com.loopers.interfaces.api;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.model.BrandCommand;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.model.MemberCommand;
import com.loopers.domain.member.service.PasswordEncryptor;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductCommand;
import com.loopers.infrastructure.brand.entity.BrandEntity;
import com.loopers.infrastructure.brand.repository.BrandJpaRepository;
import com.loopers.infrastructure.member.entity.MemberEntity;
import com.loopers.infrastructure.member.repository.MemberJpaRepository;
import com.loopers.infrastructure.product.entity.ProductEntity;
import com.loopers.infrastructure.product.repository.ProductJpaRepository;
import com.loopers.interfaces.api.order.dto.CreateOrderApiReqDto;
import com.loopers.interfaces.api.order.dto.FindOrderApiResDto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private static final String ENDPOINT_ORDERS = "/api/v1/orders";

    private final TestRestTemplate testRestTemplate;
    private final MemberJpaRepository memberJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final PasswordEncryptor passwordEncryptor;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public OrderV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        MemberJpaRepository memberJpaRepository,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        PasswordEncryptor passwordEncryptor,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.memberJpaRepository = memberJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.passwordEncryptor = passwordEncryptor;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders createAuthHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, password);
        return headers;
    }

    private MemberEntity saveMember(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        MemberCommand.SignUp command = new MemberCommand.SignUp(loginId, rawPassword, name, birthDate, email);
        Member model = Member.signUp(command, passwordEncryptor);
        return memberJpaRepository.save(MemberEntity.toEntity(model));
    }

    private BrandEntity saveBrand(String name, String description) {
        Brand brand = Brand.create(new BrandCommand.Create(name, description));
        return brandJpaRepository.save(BrandEntity.toEntity(brand));
    }

    private ProductEntity saveProduct(Long brandId, String name, int price, int stock) {
        Product product = Product.create(brandId, new ProductCommand.Create(brandId, name, price, stock));
        return productJpaRepository.save(ProductEntity.toEntity(product));
    }

    @DisplayName("POST /api/v1/orders - 주문 생성")
    @Nested
    class CreateOrder {

        @DisplayName("인증 헤더가 없으면 400 BAD_REQUEST 응답을 받는다")
        @Test
        void createOrder_missingAuthHeaders() {
            // arrange
            BrandEntity brand = saveBrand("나이키", "스포츠 브랜드");
            ProductEntity product = saveProduct(brand.getId(), "에어맥스", 120000, 10);

            CreateOrderApiReqDto request = new CreateOrderApiReqDto(
                List.of(new CreateOrderApiReqDto.OrderItemApiReqDto(product.getId(), 1))
            );
            HttpHeaders headers = new HttpHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<FindOrderApiResDto>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<FindOrderApiResDto>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }

        @DisplayName("인증에 실패하면 401 UNAUTHORIZED 응답을 받는다")
        @Test
        void createOrder_unauthorized() {
            // arrange
            BrandEntity brand = saveBrand("나이키", "스포츠 브랜드");
            ProductEntity product = saveProduct(brand.getId(), "에어맥스", 120000, 10);

            CreateOrderApiReqDto request = new CreateOrderApiReqDto(
                List.of(new CreateOrderApiReqDto.OrderItemApiReqDto(product.getId(), 1))
            );
            HttpHeaders headers = createAuthHeaders("nonexistent", "WrongPass1!");

            // act
            ParameterizedTypeReference<ApiResponse<FindOrderApiResDto>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<FindOrderApiResDto>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }

        @DisplayName("존재하지 않는 상품을 포함하면 404 NOT_FOUND 응답을 받는다")
        @Test
        void createOrder_productNotFound() {
            // arrange
            String password = "Pass1234!";
            saveMember("testuser", password, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");

            CreateOrderApiReqDto request = new CreateOrderApiReqDto(
                List.of(new CreateOrderApiReqDto.OrderItemApiReqDto(99999L, 1))
            );
            HttpHeaders headers = createAuthHeaders("testuser", password);

            // act
            ParameterizedTypeReference<ApiResponse<FindOrderApiResDto>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<FindOrderApiResDto>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }

        @DisplayName("재고가 부족하면 400 BAD_REQUEST 응답을 받는다")
        @Test
        void createOrder_insufficientStock() {
            // arrange
            String password = "Pass1234!";
            saveMember("testuser", password, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            BrandEntity brand = saveBrand("나이키", "스포츠 브랜드");
            ProductEntity product = saveProduct(brand.getId(), "에어맥스", 120000, 2);

            CreateOrderApiReqDto request = new CreateOrderApiReqDto(
                List.of(new CreateOrderApiReqDto.OrderItemApiReqDto(product.getId(), 10))
            );
            HttpHeaders headers = createAuthHeaders("testuser", password);

            // act
            ParameterizedTypeReference<ApiResponse<FindOrderApiResDto>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<FindOrderApiResDto>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }

        @DisplayName("정상 주문 시 200과 함께 totalPrice 및 orderProducts를 반환한다")
        @Test
        void createOrder_success() {
            // arrange
            String password = "Pass1234!";
            saveMember("testuser", password, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            BrandEntity brand = saveBrand("나이키", "스포츠 브랜드");
            ProductEntity product = saveProduct(brand.getId(), "에어맥스", 120000, 10);

            CreateOrderApiReqDto request = new CreateOrderApiReqDto(
                List.of(new CreateOrderApiReqDto.OrderItemApiReqDto(product.getId(), 2))
            );
            HttpHeaders headers = createAuthHeaders("testuser", password);

            // act
            ParameterizedTypeReference<ApiResponse<FindOrderApiResDto>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<FindOrderApiResDto>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().totalPrice()).isEqualTo(240000),
                () -> assertThat(response.getBody().data().orderProducts()).hasSize(1),
                () -> assertThat(response.getBody().data().orderProducts().get(0).productId()).isEqualTo(product.getId()),
                () -> assertThat(response.getBody().data().orderProducts().get(0).quantity()).isEqualTo(2)
            );
        }
    }

    @DisplayName("GET /api/v1/orders - 주문 목록 조회")
    @Nested
    class FindOrderList {

        @DisplayName("인증 헤더가 없으면 400 BAD_REQUEST 응답을 받는다")
        @Test
        void findOrderList_missingAuthHeaders() {
            // arrange - no auth headers
            HttpHeaders headers = new HttpHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<List<FindOrderApiResDto>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<FindOrderApiResDto>>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "?startAt=2020-01-01T00:00:00&endAt=2030-01-01T00:00:00",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }

        @DisplayName("인증에 실패하면 401 UNAUTHORIZED 응답을 받는다")
        @Test
        void findOrderList_unauthorized() {
            // arrange
            HttpHeaders headers = createAuthHeaders("nonexistent", "WrongPass1!");

            // act
            ParameterizedTypeReference<ApiResponse<List<FindOrderApiResDto>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<FindOrderApiResDto>>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "?startAt=2020-01-01T00:00:00&endAt=2030-01-01T00:00:00",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }

        @DisplayName("기간 내 주문이 없으면 빈 리스트를 반환한다")
        @Test
        void findOrderList_empty() {
            // arrange
            String password = "Pass1234!";
            saveMember("testuser", password, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            HttpHeaders headers = createAuthHeaders("testuser", password);

            // act
            ParameterizedTypeReference<ApiResponse<List<FindOrderApiResDto>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<FindOrderApiResDto>>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "?startAt=2020-01-01T00:00:00&endAt=2020-12-31T23:59:59",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data()).isEmpty()
            );
        }

        @DisplayName("기간 내 주문이 있으면 주문 목록을 반환한다")
        @Test
        void findOrderList_success() {
            // arrange
            String password = "Pass1234!";
            saveMember("testuser", password, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            BrandEntity brand = saveBrand("나이키", "스포츠 브랜드");
            ProductEntity product = saveProduct(brand.getId(), "에어맥스", 120000, 10);

            HttpHeaders headers = createAuthHeaders("testuser", password);

            // 주문 생성
            CreateOrderApiReqDto createRequest = new CreateOrderApiReqDto(
                List.of(new CreateOrderApiReqDto.OrderItemApiReqDto(product.getId(), 1))
            );
            testRestTemplate.exchange(
                ENDPOINT_ORDERS,
                HttpMethod.POST,
                new HttpEntity<>(createRequest, headers),
                new ParameterizedTypeReference<ApiResponse<FindOrderApiResDto>>() {}
            );

            // act
            ParameterizedTypeReference<ApiResponse<List<FindOrderApiResDto>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<FindOrderApiResDto>>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "?startAt=2020-01-01T00:00:00&endAt=2099-12-31T23:59:59",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data()).hasSize(1)
            );
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId} - 주문 단건 조회")
    @Nested
    class FindOrder {

        @DisplayName("인증 헤더가 없으면 400 BAD_REQUEST 응답을 받는다")
        @Test
        void findOrder_missingAuthHeaders() {
            // arrange - no auth headers
            HttpHeaders headers = new HttpHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<FindOrderApiResDto>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<FindOrderApiResDto>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "/1",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }

        @DisplayName("인증에 실패하면 401 UNAUTHORIZED 응답을 받는다")
        @Test
        void findOrder_unauthorized() {
            // arrange
            HttpHeaders headers = createAuthHeaders("nonexistent", "WrongPass1!");

            // act
            ParameterizedTypeReference<ApiResponse<FindOrderApiResDto>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<FindOrderApiResDto>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "/1",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }

        @DisplayName("존재하지 않는 주문을 조회하면 404 NOT_FOUND 응답을 받는다")
        @Test
        void findOrder_notFound() {
            // arrange
            String password = "Pass1234!";
            saveMember("testuser", password, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            HttpHeaders headers = createAuthHeaders("testuser", password);

            // act
            ParameterizedTypeReference<ApiResponse<FindOrderApiResDto>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<FindOrderApiResDto>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "/99999",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }

        @DisplayName("정상 조회 시 orderProducts를 포함한 주문 정보를 반환한다")
        @Test
        void findOrder_success() {
            // arrange
            String password = "Pass1234!";
            saveMember("testuser", password, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            BrandEntity brand = saveBrand("나이키", "스포츠 브랜드");
            ProductEntity product = saveProduct(brand.getId(), "에어맥스", 120000, 10);

            HttpHeaders headers = createAuthHeaders("testuser", password);

            // 주문 생성
            CreateOrderApiReqDto createRequest = new CreateOrderApiReqDto(
                List.of(new CreateOrderApiReqDto.OrderItemApiReqDto(product.getId(), 3))
            );
            ParameterizedTypeReference<ApiResponse<FindOrderApiResDto>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<FindOrderApiResDto>> createResponse =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(createRequest, headers),
                    responseType
                );
            Long orderId = createResponse.getBody().data().id();

            // act
            ResponseEntity<ApiResponse<FindOrderApiResDto>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS + "/" + orderId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().id()).isEqualTo(orderId),
                () -> assertThat(response.getBody().data().totalPrice()).isEqualTo(360000),
                () -> assertThat(response.getBody().data().orderProducts()).hasSize(1),
                () -> assertThat(response.getBody().data().orderProducts().get(0).productId()).isEqualTo(product.getId()),
                () -> assertThat(response.getBody().data().orderProducts().get(0).quantity()).isEqualTo(3)
            );
        }
    }
}
