package com.loopers.interfaces.api.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserRequest;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BrandLikeApiE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @BeforeEach
    void setUp() {
        UserRequest.SignupRequest signupRequest = new UserRequest.SignupRequest(
                "testuser", "Hx7!mK2@", "테스터", "1994-11-15", "test@example.com");
        testRestTemplate.postForEntity("/api/v1/users", signupRequest, ApiResponse.class);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "testuser");
        headers.set("X-Loopers-LoginPw", "Hx7!mK2@");
        return headers;
    }

    private Brand createActiveBrand(String name) {
        return brandRepository.save(Brand.create(name, name + " 설명"));
    }

    private String likeUrl(Long brandId) {
        return "/api/v1/brands/" + brandId + "/likes";
    }

    @DisplayName("POST /api/v1/brands/{brandId}/likes")
    @Nested
    class 브랜드_좋아요_등록 {

        @Test
        void 좋아요_등록에_성공하면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    likeUrl(brand.getId()), HttpMethod.POST, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 중복_좋아요는_409_Conflict를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            testRestTemplate.exchange(
                    likeUrl(brand.getId()), HttpMethod.POST, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    likeUrl(brand.getId()), HttpMethod.POST, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 비활성_브랜드이면_404_Not_Found를_반환한다() {
            // arrange
            Brand brand = Brand.create("비활성", "설명");
            brand.changeStatus(BrandStatus.INACTIVE);
            Brand saved = brandRepository.save(brand);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    likeUrl(saved.getId()), HttpMethod.POST, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 인증_없이_요청하면_401_Unauthorized를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    likeUrl(brand.getId()), HttpMethod.POST, new HttpEntity<>(new HttpHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("DELETE /api/v1/brands/{brandId}/likes")
    @Nested
    class 브랜드_좋아요_취소 {

        @Test
        void 좋아요_취소에_성공하면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            testRestTemplate.exchange(
                    likeUrl(brand.getId()), HttpMethod.POST, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    likeUrl(brand.getId()), HttpMethod.DELETE, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 좋아요가_없으면_404_Not_Found를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    likeUrl(brand.getId()), HttpMethod.DELETE, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
