package com.loopers.interfaces.api.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.infrastructure.brand.BrandJpaRepository;
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
class BrandV1ApiE2ETest {

    private static final String VALID_BRAND_NAME = "아디다스";
    private static final Function<Long, String> ENDPOINT_GET = id -> "/api/v1/brands/" + id;
    private static final Long NOT_EXISTED_BRAND_ID = 999L;

    private static final String SIGNUP_ENDPOINT = "/api/v1/users/signup";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String VALID_LOGIN_ID = "branduser1";
    private static final String VALID_PASSWORD = "brand@1234";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final BrandJpaRepository brandJpaRepository;

    @Autowired
    public BrandV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp,
            BrandJpaRepository brandJpaRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.brandJpaRepository = brandJpaRepository;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    // AuthInterceptor 통과를 위한 유저 사전 등록
    void signUpUser() {
        UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, "브랜드유저", "1990-01-01", "brand@test.com"
        );
        testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(signupRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});
    }

    HttpHeaders createUserHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, VALID_LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, VALID_PASSWORD);
        return headers;
    }

    @DisplayName("GET /api/v1/brands/{id}")
    @Nested
    class GetBrandDetails {
        @DisplayName("존재하는 brandId로 요청하면, 200 OK와 브랜드 정보를 반환한다.")
        @Test
        void returnsBrandDetails_whenGivenBrandIdIsValid() {
            // arrange
            signUpUser();
            Brand existingBrand = brandJpaRepository.save(new Brand(VALID_BRAND_NAME));
            String requestUrl = ENDPOINT_GET.apply(existingBrand.getId());
            HttpHeaders headers = createUserHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(existingBrand.getId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(VALID_BRAND_NAME)
            );
        }

        @DisplayName("존재하지 않는 brandId로 요청하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenBrandIdDoesNotExist() {
            // arrange
            signUpUser();
            String requestUrl = ENDPOINT_GET.apply(NOT_EXISTED_BRAND_ID);
            HttpHeaders headers = createUserHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }
}
