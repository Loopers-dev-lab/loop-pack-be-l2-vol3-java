package com.loopers.interfaces.api.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;

import static com.loopers.interfaces.api.ApiResponse.Metadata.Result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
class LikeV1ApiE2ETest {

    private static final String ENDPOINT_LIKES = "/api/v1/likes";
    private static final String LOGIN_ID = "likeuser";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private BrandService brandService;
    @Autowired
    private ProductService productService;

    private Long productId;

    @BeforeEach
    void setUp() {
        UserV1Dto.SignUpRequest signUp = new UserV1Dto.SignUpRequest(
            "likeuser", "SecurePass1!", "like@example.com", "1990-01-15", "MALE");
        ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> signUpResponse =
            testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(signUp),
                new ParameterizedTypeReference<>() {});
        assertThat(signUpResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(signUpResponse.getBody()).isNotNull();
        assertThat(signUpResponse.getBody().meta().result()).isEqualTo(Result.SUCCESS);

        BrandModel brand = brandService.registerBrand("E2E브랜드");
        ProductModel product = productService.registerProduct(brand.getId(), "E2E상품", new BigDecimal("10000"), 5);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Loopers-LoginId", LOGIN_ID);
        return h;
    }

    @Test
    @DisplayName("중복 로그인 ID로 회원가입 시 409 Conflict를 반환한다 (준비 데이터 규칙)")
    void signUp_withDuplicateLoginId_shouldReturnConflict() {
        UserV1Dto.SignUpRequest duplicateRequest = new UserV1Dto.SignUpRequest(
            "likeuser", "OtherPass1!", "other@example.com", "1995-06-01", "FEMALE");

        ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response = testRestTemplate.exchange(
            "/api/v1/users", HttpMethod.POST, new HttpEntity<>(duplicateRequest),
            new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @DisplayName("POST /api/v1/likes - 좋아요 추가")
    @Nested
    class AddLike {

        @Test
        void addLike_withValidRequest_shouldReturn201() {
            LikeV1Dto.AddLikeRequest request = new LikeV1Dto.AddLikeRequest(productId);

            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response = testRestTemplate.exchange(
                ENDPOINT_LIKES, HttpMethod.POST, new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {});

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                () -> assertThat(response.getBody().data().productId()).isEqualTo(productId)
            );
        }

        @Test
        void addLike_withoutLogin_shouldReturn401() {
            LikeV1Dto.AddLikeRequest request = new LikeV1Dto.AddLikeRequest(productId);

            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response = testRestTemplate.exchange(
                ENDPOINT_LIKES, HttpMethod.POST, new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("DELETE /api/v1/likes/{productId} - 좋아요 취소")
    @Nested
    class RemoveLike {

        @Test
        void removeLike_withValidRequest_shouldReturn204() {
            LikeV1Dto.AddLikeRequest addReq = new LikeV1Dto.AddLikeRequest(productId);
            testRestTemplate.exchange(ENDPOINT_LIKES, HttpMethod.POST, new HttpEntity<>(addReq, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>>() {});

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_LIKES + "/" + productId, HttpMethod.DELETE, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    @DisplayName("GET /api/v1/likes - 내 좋아요 목록")
    @Nested
    class GetMyLikes {

        @Test
        void getMyLikes_withValidRequest_shouldReturn200() {
            LikeV1Dto.AddLikeRequest addReq = new LikeV1Dto.AddLikeRequest(productId);
            testRestTemplate.exchange(ENDPOINT_LIKES, HttpMethod.POST, new HttpEntity<>(addReq, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>>() {});

            ResponseEntity<ApiResponse<LikeV1Dto.PagedLikesResponse>> response = testRestTemplate.exchange(
                ENDPOINT_LIKES + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {});

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content()).hasSize(1),
                () -> assertThat(response.getBody().data().content().get(0).productId()).isEqualTo(productId)
            );
        }

        @Test
        void getMyLikes_withoutLogin_shouldReturn401() {
            ResponseEntity<ApiResponse<LikeV1Dto.PagedLikesResponse>> response = testRestTemplate.exchange(
                ENDPOINT_LIKES, HttpMethod.GET, new HttpEntity<>(null), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
