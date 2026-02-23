package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.interfaces.api.brand.BrandAdminV1Dto;
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
class BrandAdminV1ApiE2ETest {

    private static final String VALID_BRAND_NAME = "아디다스";
    private static final String NEW_BRAND_NAME = "나이키";
    private static final String ENDPOINT_POST = "/api-admin/v1/brands";
    private static final String ENDPOINT_GET_LIST = "/api-admin/v1/brands";
    private static final Function<Long, String>  ENDPOINT_GET = id -> "/api-admin/v1/brands/" + id;
    private static final Long NOT_EXISTED_BRAND_ID = 999L;

    private static final String HEADER_ADMIN_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final BrandJpaRepository brandJpaRepository;

    @Autowired
    public BrandAdminV1ApiE2ETest(
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

    HttpHeaders createAdminHeaders(){
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_ADMIN_LDAP, ADMIN_LDAP_VALUE);
        return headers;
    }

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    class Register {
        @DisplayName("정상적인 브랜드명으로 등록하면, 200 OK와 브랜드 정보를 반환한다.")
        @Test
        void returnBrandInfo_whenRegisterIsValid() {
            // arrange
            // 헤더에 관리자 인증 정보 세팅
            HttpHeaders headers = createAdminHeaders();
            BrandAdminV1Dto.RegisterRequest request = new BrandAdminV1Dto.RegisterRequest(VALID_BRAND_NAME);
            HttpEntity<BrandAdminV1Dto.RegisterRequest> httpEntity = new HttpEntity<>(request, headers);

            // act
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_POST, HttpMethod.POST, httpEntity, responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().id()).isPositive(),
                () -> assertThat(response.getBody().data().name()).isEqualTo(VALID_BRAND_NAME)
            );
        }

        @DisplayName("관리자 인증 헤더가 누락되면, 401 UNAUTHORIZED 응답을 받는다")
        @Test
        void returnsUnauthorized_whenHeadersMissing(){
            // arrange
            BrandAdminV1Dto.RegisterRequest request = new BrandAdminV1Dto.RegisterRequest(VALID_BRAND_NAME);
            HttpEntity<BrandAdminV1Dto.RegisterRequest> httpEntity = new HttpEntity<>(request);

            // act
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(ENDPOINT_POST, HttpMethod.POST, httpEntity, responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.UNAUTHORIZED.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("중복되는 브랜드명으로 등록하면, 409 에러를 반환한다")
        @Test
        void returnsConflict_whenBrandNameIsNotUnique(){
            // arrange
            brandJpaRepository.save(new Brand(VALID_BRAND_NAME));

            HttpHeaders headers = createAdminHeaders();
            BrandAdminV1Dto.RegisterRequest request = new BrandAdminV1Dto.RegisterRequest(VALID_BRAND_NAME);
            HttpEntity<BrandAdminV1Dto.RegisterRequest> httpEntity = new HttpEntity<>(request, headers);

            // act
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(ENDPOINT_POST, HttpMethod.POST, httpEntity, responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.CONFLICT.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{id}")
    @Nested
    class GetBrandDetails {
        @DisplayName("존재하는 brandId로 요청하면, 200 OK와 브랜드 상세 정보를 반환한다.")
        @Test
        void returnsBrandDetails_whenGivenBrandIdIsValid() {
            // arrange
            Brand existingBrand = brandJpaRepository.save(new Brand(VALID_BRAND_NAME));
            String requestUrl = ENDPOINT_GET.apply(existingBrand.getId());
            HttpHeaders headers = createAdminHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(existingBrand.getId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(existingBrand.getName())
            );
        }

        @DisplayName("존재하지 않는 brandId로 요청하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenBrandIdDoesNotExist() {
            // arrange
            String requestUrl = ENDPOINT_GET.apply(NOT_EXISTED_BRAND_ID);
            HttpHeaders headers = createAdminHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    class GetBrandList {
        @DisplayName("등록된 브랜드가 있을 때 목록을 조회하면, 200 OK와 브랜드 목록을 반환한다.")
        @Test
        void returnsBrandList_whenBrandsExist() {
            // arrange
            brandJpaRepository.save(new Brand(VALID_BRAND_NAME));
            HttpHeaders headers = createAdminHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandListResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_GET_LIST, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(1),
                    () -> assertThat(response.getBody().data().brands().get(0).name()).isEqualTo(VALID_BRAND_NAME)
            );
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{id}")
    @Nested
    class UpdateBrand {
        @DisplayName("중복되지 않는 브랜드명으로 수정하면, 200 OK와 수정된 브랜드 정보를 반환한다.")
        @Test
        void returnsBrandInfo_whenUpdateIsValid() {
            // arrange
            Brand existingBrand = brandJpaRepository.save(new Brand(VALID_BRAND_NAME));
            HttpHeaders headers = createAdminHeaders();
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest(NEW_BRAND_NAME);
            HttpEntity<BrandAdminV1Dto.UpdateRequest> httpEntity = new HttpEntity<>(request, headers);
            String requestUrl = ENDPOINT_GET.apply(existingBrand.getId());

            // act
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.PUT, httpEntity, responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(NEW_BRAND_NAME)
            );
        }

        @DisplayName("중복되는 브랜드명으로 수정하면, 409 CONFLICT 응답을 받는다.")
        @Test
        void returnsConflict_whenBrandNameIsNotUnique() {
            // arrange
            Brand existingBrand = brandJpaRepository.save(new Brand(VALID_BRAND_NAME));
            brandJpaRepository.save(new Brand(NEW_BRAND_NAME));
            HttpHeaders headers = createAdminHeaders();
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest(NEW_BRAND_NAME);
            HttpEntity<BrandAdminV1Dto.UpdateRequest> httpEntity = new HttpEntity<>(request, headers);
            String requestUrl = ENDPOINT_GET.apply(existingBrand.getId());

            // act
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.PUT, httpEntity, responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.CONFLICT.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("존재하지 않는 brandId로 수정하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenBrandIdDoesNotExist() {
            // arrange
            HttpHeaders headers = createAdminHeaders();
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest(NEW_BRAND_NAME);
            HttpEntity<BrandAdminV1Dto.UpdateRequest> httpEntity = new HttpEntity<>(request, headers);
            String requestUrl = ENDPOINT_GET.apply(NOT_EXISTED_BRAND_ID);

            // act
            ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.PUT, httpEntity, responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{id}")
    @Nested
    class DeleteBrand {
        @DisplayName("존재하는 brandId로 삭제하면, 200 OK를 반환한다.")
        @Test
        void returnsSuccess_whenDeleteIsValid() {
            // arrange
            Brand existingBrand = brandJpaRepository.save(new Brand(VALID_BRAND_NAME));
            HttpHeaders headers = createAdminHeaders();
            String requestUrl = ENDPOINT_GET.apply(existingBrand.getId());

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.DELETE, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
            );
        }

        @DisplayName("존재하지 않는 brandId로 삭제하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenBrandIdDoesNotExist() {
            // arrange
            HttpHeaders headers = createAdminHeaders();
            String requestUrl = ENDPOINT_GET.apply(NOT_EXISTED_BRAND_ID);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.DELETE, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }
}
