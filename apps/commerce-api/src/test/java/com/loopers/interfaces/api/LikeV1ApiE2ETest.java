package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserFixture;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.like.LikeV1Dto;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private static final String PRODUCTS_ENDPOINT = "/api/v1/products";
    private static final String MY_LIKES_ENDPOINT = "/api/v1/users/me/likes";
    private static final String RAW_PASSWORD = "TestPass1!";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final LikeJpaRepository likeJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public LikeV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            UserJpaRepository userJpaRepository,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            LikeJpaRepository likeJpaRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.likeJpaRepository = likeJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    private User savedUser;
    private Product savedProduct;

    @BeforeEach
    void setUp() {
        String encodedPassword = bCryptPasswordEncoder.encode(RAW_PASSWORD);
        savedUser = userJpaRepository.save(
                UserFixture.builder()
                           .loginId("likeTestUser")
                           .password(encodedPassword)
                           .build()
        );

        Brand brand = brandJpaRepository.save(Brand.create("나이키", "스포츠"));
        savedProduct = productJpaRepository.save(Product.create(brand.getId(), "에어맥스", null, 150000, 10));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", savedUser.getLoginId());
        headers.set("X-Loopers-LoginPw", RAW_PASSWORD);
        return headers;
    }

    @DisplayName("좋아요 등록 시")
    @Nested
    class Register {

        @DisplayName("유효한 요청이면, 201 Created와 좋아요 정보를 반환한다.")
        @Test
        void returnsCreated_whenValidRequest() {
            // arrange
            String url = PRODUCTS_ENDPOINT + "/" + savedProduct.getId() + "/likes";
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders());

            // act
            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response =
                    testRestTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody().data().productId()).isEqualTo(savedProduct.getId()),
                    () -> assertThat(response.getBody().data().userId()).isEqualTo(savedUser.getId())
            );
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요하면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenDuplicate() {
            // arrange
            String url = PRODUCTS_ENDPOINT + "/" + savedProduct.getId() + "/likes";
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders());
            testRestTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("HIDDEN 상품에 좋아요하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductIsHidden() {
            // arrange
            savedProduct.changeVisibility(Product.Visibility.HIDDEN);
            productJpaRepository.save(savedProduct);
            String url = PRODUCTS_ENDPOINT + "/" + savedProduct.getId() + "/likes";
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // arrange
            String url = PRODUCTS_ENDPOINT + "/" + Long.MAX_VALUE + "/likes";
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("잘못된 비밀번호로 요청하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenWrongPassword() {
            // arrange
            String url = PRODUCTS_ENDPOINT + "/" + savedProduct.getId() + "/likes";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", savedUser.getLoginId());
            headers.set("X-Loopers-LoginPw", "WrongPass1!");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("좋아요 취소 시")
    @Nested
    class Cancel {

        @DisplayName("좋아요가 존재하면, 200 OK를 반환하고 좋아요가 삭제된다.")
        @Test
        void returnsOk_whenLikeExists() {
            // arrange
            String url = PRODUCTS_ENDPOINT + "/" + savedProduct.getId() + "/likes";
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders());
            testRestTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(url, HttpMethod.DELETE, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(likeJpaRepository.findByUserIdAndProductId(savedUser.getId(), savedProduct.getId())).isEmpty()
            );
        }

        @DisplayName("좋아요하지 않은 상품에 취소 요청해도, 200 OK를 반환한다.")
        @Test
        void returnsOk_whenNotLiked() {
            // arrange
            String url = PRODUCTS_ENDPOINT + "/" + savedProduct.getId() + "/likes";
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(url, HttpMethod.DELETE, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("좋아요 목록 조회 시")
    @Nested
    class GetLikes {

        @DisplayName("좋아요 목록을 조회하면, 200 OK와 목록을 반환한다.")
        @Test
        void returnsOk_withLikes() {
            // arrange
            String likeUrl = PRODUCTS_ENDPOINT + "/" + savedProduct.getId() + "/likes";
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders());
            testRestTemplate.exchange(likeUrl, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // act
            ResponseEntity<ApiResponse<List<LikeV1Dto.LikeResponse>>> response =
                    testRestTemplate.exchange(MY_LIKES_ENDPOINT, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).hasSize(1),
                    () -> assertThat(response.getBody().data().get(0).productId()).isEqualTo(savedProduct.getId())
            );
        }

        @DisplayName("삭제된 상품의 좋아요는 목록에서 제외된다.")
        @Test
        void excludesDeletedProductFromLikesList() {
            // arrange
            String likeUrl = PRODUCTS_ENDPOINT + "/" + savedProduct.getId() + "/likes";
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders());
            testRestTemplate.exchange(likeUrl, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            savedProduct.delete();
            productJpaRepository.save(savedProduct);

            // act
            ResponseEntity<ApiResponse<List<LikeV1Dto.LikeResponse>>> response =
                    testRestTemplate.exchange(MY_LIKES_ENDPOINT, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).isEmpty()
            );
        }

        @DisplayName("HIDDEN 상태인 상품의 좋아요는 목록에서 제외된다.")
        @Test
        void excludesHiddenProductFromLikesList() {
            // arrange
            String likeUrl = PRODUCTS_ENDPOINT + "/" + savedProduct.getId() + "/likes";
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders());
            testRestTemplate.exchange(likeUrl, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            savedProduct.changeVisibility(Product.Visibility.HIDDEN);
            productJpaRepository.save(savedProduct);

            // act
            ResponseEntity<ApiResponse<List<LikeV1Dto.LikeResponse>>> response =
                    testRestTemplate.exchange(MY_LIKES_ENDPOINT, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).isEmpty()
            );
        }

        @DisplayName("좋아요가 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoLikes() {
            // arrange
            HttpEntity<Void> entity = new HttpEntity<>(userHeaders());

            // act
            ResponseEntity<ApiResponse<List<LikeV1Dto.LikeResponse>>> response =
                    testRestTemplate.exchange(MY_LIKES_ENDPOINT, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).isEmpty()
            );
        }
    }
}
