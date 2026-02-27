package com.loopers.interfaces.api.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.support.error.ErrorType;
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
import org.springframework.http.ResponseEntity;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private static final String VALID_LOGIN_ID = "likeuser1";
    private static final String VALID_PASSWORD = "like@1234";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private static final String SIGNUP_ENDPOINT = "/api/v1/users/signup";
    private static final Function<Long, String> ENDPOINT_CREATE_LIKE = productId -> "/api/v1/likes/" + productId;
    private static final Function<Long, String> ENDPOINT_DELETE_LIKE = productId -> "/api/v1/likes/" + productId;
    private static final String ENDPOINT_GET_LIKES = "/api/v1/likes";

    private static final String VALID_PRODUCT_NAME = "나이키 에어맥스";
    private static final int VALID_PRICE = 10000;
    private static final int VALID_STOCK = 100;
    private static final Long NOT_EXISTED_PRODUCT_ID = 999L;

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final LikeJpaRepository likeJpaRepository;
    private final UserJpaRepository userJpaRepository;

    @Autowired
    public LikeV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            LikeJpaRepository likeJpaRepository,
            UserJpaRepository userJpaRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.likeJpaRepository = likeJpaRepository;
        this.userJpaRepository = userJpaRepository;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    // AuthInterceptor 통과를 위한 유저 사전 등록 및 ID 반환
    Long signUpAndGetUserId() {
        UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, "좋아요유저", "1990-01-01", "like@test.com"
        );
        testRestTemplate.exchange(
                SIGNUP_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(signupRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
        );
        return userJpaRepository.findByLoginId(VALID_LOGIN_ID)
                .orElseThrow().getId();
    }

    // 사용자 헤더 생성 메서드
    HttpHeaders createUserHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, VALID_LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, VALID_PASSWORD);
        return headers;
    }

    @DisplayName("POST /api/v1/likes/{productId}")
    @Nested
    class CreateLike {

        @DisplayName("인증된 회원이 존재하는 상품에 좋아요 등록하면, 200 OK와 좋아요 정보를 반환한다.")
        @Test
        void returnsLikeResponse_whenLikeCreatedSuccessfully() {
            // arrange
            signUpAndGetUserId();
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            HttpHeaders headers = createUserHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_CREATE_LIKE.apply(product.getId()),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().likeId()).isPositive(),
                    () -> assertThat(response.getBody().data().productId()).isEqualTo(product.getId()),
                    () -> assertThat(response.getBody().data().productName()).isEqualTo(VALID_PRODUCT_NAME)
            );
        }

        @DisplayName("인증 헤더 없이 좋아요 등록을 요청하면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderMissing() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));

            // act
            ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_CREATE_LIKE.apply(product.getId()),
                    HttpMethod.POST,
                    HttpEntity.EMPTY,
                    responseType
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.UNAUTHORIZED.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요하면, 409 CONFLICT를 반환한다.")
        @Test
        void returnsConflict_whenAlreadyLiked() {
            // arrange
            Long userId = signUpAndGetUserId();
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            likeJpaRepository.save(new Like(userId, product.getId()));
            HttpHeaders headers = createUserHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_CREATE_LIKE.apply(product.getId()),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.CONFLICT.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExist() {
            // arrange
            signUpAndGetUserId();
            HttpHeaders headers = createUserHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_CREATE_LIKE.apply(NOT_EXISTED_PRODUCT_ID),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @DisplayName("DELETE /api/v1/likes/{productId}")
    @Nested
    class DeleteLike {

        @DisplayName("인증된 회원이 좋아요한 상품을 취소하면, 200 OK를 반환한다.")
        @Test
        void returnsSuccess_whenLikeDeletedSuccessfully() {
            // arrange
            Long userId = signUpAndGetUserId();
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            likeJpaRepository.save(new Like(userId, product.getId()));
            HttpHeaders headers = createUserHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_DELETE_LIKE.apply(product.getId()),
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
            );
        }

        @DisplayName("좋아요하지 않은 상품을 취소하면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenLikeNotExist() {
            // arrange
            signUpAndGetUserId();
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            HttpHeaders headers = createUserHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_DELETE_LIKE.apply(product.getId()),
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @DisplayName("GET /api/v1/likes")
    @Nested
    class GetLikes {

        @DisplayName("인증된 회원이 자신의 좋아요 목록을 조회하면, 200 OK와 목록을 반환한다.")
        @Test
        void returnsLikedProductList_whenUserHasLikes() {
            // arrange
            Long userId = signUpAndGetUserId();
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            likeJpaRepository.save(new Like(userId, product.getId()));
            HttpHeaders headers = createUserHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikedProductListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<LikeV1Dto.LikedProductListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_GET_LIKES,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().likes()).hasSize(1),
                    () -> assertThat(response.getBody().data().likes().get(0).productName()).isEqualTo(VALID_PRODUCT_NAME)
            );
        }

        @DisplayName("좋아요한 상품이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoLikes() {
            // arrange
            signUpAndGetUserId();
            HttpHeaders headers = createUserHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikedProductListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<LikeV1Dto.LikedProductListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_GET_LIKES,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().likes()).isEmpty()
            );
        }

    }
}
