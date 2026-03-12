package com.loopers.interfaces.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductLikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.product.dto.ProductLikeV1Dto;
import com.loopers.interfaces.user.dto.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductLikeV1ApiE2ETest {

    private static final String ENDPOINT_SIGNUP = "/api/v1/users/signup";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final ProductLikeJpaRepository productLikeJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @Autowired
    public ProductLikeV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        ProductLikeJpaRepository productLikeJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.productLikeJpaRepository = productLikeJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        // 사용자 등록
        UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
            "testuser1",
            "Test1234!",
            "홍길동",
            "19900101",
            "test@example.com"
        );
        ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> signupType = new ParameterizedTypeReference<>() {};
        testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(signupRequest), signupType);

        // 브랜드 및 상품 등록
        BrandModel brand = brandJpaRepository.save(BrandModel.create("나이키"));
        ProductModel product = productJpaRepository.save(ProductModel.create(brand.getId(), "에어맥스", 150000, 100));
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, "testuser1");
        headers.set(HEADER_LOGIN_PW, "Test1234!");
        return headers;
    }

    private String likeEndpoint(Long productId) {
        return "/api/v1/products/" + productId + "/likes";
    }

    private void likeProduct(Long productId) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        testRestTemplate.exchange(likeEndpoint(productId), HttpMethod.POST, new HttpEntity<>(null, authHeaders()), responseType);
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class Like {

        @DisplayName("유효한 인증 헤더로 좋아요를 등록하면, 성공 응답을 반환한다.")
        @Test
        void returnsSuccess_whenValidRequest() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(likeEndpoint(productId), HttpMethod.POST, new HttpEntity<>(null, authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(productLikeJpaRepository.count()).isEqualTo(1)
            );
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요하면, 409 CONFLICT 응답을 받는다.")
        @Test
        void throwsConflict_whenAlreadyLiked() {
            // arrange
            likeProduct(productId);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(likeEndpoint(productId), HttpMethod.POST, new HttpEntity<>(null, authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(productLikeJpaRepository.count()).isEqualTo(1)
            );
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(likeEndpoint(999L), HttpMethod.POST, new HttpEntity<>(null, authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(productLikeJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenNoAuthHeaders() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(likeEndpoint(productId), HttpMethod.POST, null, responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(productLikeJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("잘못된 비밀번호로 요청하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenPasswordIsIncorrect() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, "testuser1");
            headers.set(HEADER_LOGIN_PW, "Wrong1234!");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(likeEndpoint(productId), HttpMethod.POST, new HttpEntity<>(null, headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(productLikeJpaRepository.count()).isEqualTo(0)
            );
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class Unlike {

        @DisplayName("좋아요한 상품의 좋아요를 취소하면, 성공 응답을 반환한다.")
        @Test
        void returnsSuccess_whenLikeExists() {
            // arrange
            likeProduct(productId);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(likeEndpoint(productId), HttpMethod.DELETE, new HttpEntity<>(null, authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(productLikeJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("좋아요 기록이 없는 상품의 좋아요를 취소하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void throwsNotFound_whenLikeDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(likeEndpoint(productId), HttpMethod.DELETE, new HttpEntity<>(null, authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenNoAuthHeaders() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(likeEndpoint(productId), HttpMethod.DELETE, null, responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }

        @DisplayName("잘못된 비밀번호로 요청하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenPasswordIsIncorrect() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, "testuser1");
            headers.set(HEADER_LOGIN_PW, "Wrong1234!");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(likeEndpoint(productId), HttpMethod.DELETE, new HttpEntity<>(null, headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }
    }

    @DisplayName("GET /api/v1/users/me/likes")
    @Nested
    class GetMyLikes {

        private static final String ENDPOINT_MY_LIKES = "/api/v1/users/me/likes";

        @DisplayName("좋아요한 상품이 있으면, 좋아요 목록을 반환한다.")
        @Test
        void returnsLikeList_whenLikesExist() {
            // arrange
            likeProduct(productId);

            // act
            ParameterizedTypeReference<ApiResponse<ProductLikeV1Dto.ListResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductLikeV1Dto.ListResponse>> response =
                testRestTemplate.exchange(ENDPOINT_MY_LIKES, HttpMethod.GET, new HttpEntity<>(null, authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().items()).hasSize(1),
                () -> assertThat(response.getBody().data().items().get(0).productId()).isEqualTo(productId)
            );
        }

        @DisplayName("좋아요한 상품이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoLikesExist() {
            // act
            ParameterizedTypeReference<ApiResponse<ProductLikeV1Dto.ListResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductLikeV1Dto.ListResponse>> response =
                testRestTemplate.exchange(ENDPOINT_MY_LIKES, HttpMethod.GET, new HttpEntity<>(null, authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().items()).isEmpty()
            );
        }

        @DisplayName("좋아요 후 취소하면, 목록에서 사라진다.")
        @Test
        void returnsEmptyList_afterUnlike() {
            // arrange
            likeProduct(productId);

            ParameterizedTypeReference<ApiResponse<Object>> unlikeType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(likeEndpoint(productId), HttpMethod.DELETE, new HttpEntity<>(null, authHeaders()), unlikeType);

            // act
            ParameterizedTypeReference<ApiResponse<ProductLikeV1Dto.ListResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductLikeV1Dto.ListResponse>> response =
                testRestTemplate.exchange(ENDPOINT_MY_LIKES, HttpMethod.GET, new HttpEntity<>(null, authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().items()).isEmpty()
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenNoAuthHeaders() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_MY_LIKES, HttpMethod.GET, null, responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }

        @DisplayName("잘못된 비밀번호로 요청하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenPasswordIsIncorrect() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, "testuser1");
            headers.set(HEADER_LOGIN_PW, "Wrong1234!");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_MY_LIKES, HttpMethod.GET, new HttpEntity<>(null, headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }
    }
}
