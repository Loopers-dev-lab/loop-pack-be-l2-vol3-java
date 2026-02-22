package com.loopers.interfaces.api.brand.v1;

import static com.loopers.interfaces.api.brand.v1.BrandSteps.createBrand;
import static com.loopers.support.E2ETestHelper.adminAuthHeaders;
import static com.loopers.support.E2ETestHelper.assertErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.brand.v1.BrandDto.BrandResponse;
import com.loopers.interfaces.api.brand.v1.BrandDto.CreateBrandRequest;
import com.loopers.support.BaseE2ETest;
import com.loopers.support.error.ErrorType;

class BrandV1AdminApiE2ETest extends BaseE2ETest {

    private static final String BRAND_ADMIN_ENDPOINT = "/api-admin/v1/brands";

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    class CreateBrand {

        @DisplayName("유효한 정보를 입력하면, 브랜드 생성에 성공한다.")
        @Test
        void createsBrand_whenValidInputProvided() {
            // arrange
            var request = new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "브랜드 설명");

            // act
            var response = createBrand(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().brandId()).isNotNull()
            );
        }

        @DisplayName("X-Loopers-Ldap 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenNoLdapHeader() {
            // arrange
            var request = new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "브랜드 설명");

            // act
            var response = createBrand(testRestTemplate, request, new HttpHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }

        @DisplayName("X-Loopers-Ldap 헤더 값이 잘못되면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenLdapHeaderValueIsWrong() {
            // arrange
            var request = new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "브랜드 설명");
            var headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong.value");

            // act
            var response = createBrand(testRestTemplate, request, headers);

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }

        @DisplayName("브랜드명이 빈 값이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            var request = new BrandDto.CreateBrandRequest("", "https://example.com/logo.png", "브랜드 설명");

            // act
            var response = createBrand(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST);
        }

        @DisplayName("로고 URL이 빈 값이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenLogoUrlIsBlank() {
            // arrange
            var request = new BrandDto.CreateBrandRequest("브랜드명", "", "브랜드 설명");

            // act
            var response = createBrand(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST);
        }

        @DisplayName("브랜드명 길이가 유효하지 않으면, INVALID_BRAND_NAME 에러 응답을 받는다.")
        @ParameterizedTest(name = "길이가 {0}인 브랜드명")
        @ValueSource(ints = {1, 51})
        void returnsInvalidBrandName_whenNameLengthIsInvalid(int length) {
            // arrange
            var name = "a".repeat(length);
            var request = new BrandDto.CreateBrandRequest(name, "https://example.com/logo.png", "브랜드 설명");

            // act
            var response = createBrand(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_BRAND_NAME);
        }

        @DisplayName("이미 존재하는 브랜드명으로 등록하면, ALREADY_EXIST_BRAND_NAME 에러 응답을 받는다.")
        @Test
        void returnsAlreadyExistBrandName_whenDuplicateNameProvided() {
            // arrange
            var request = new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "브랜드 설명");
            createBrand(testRestTemplate, request, adminAuthHeaders());

            // act
            var response = createBrand(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.ALREADY_EXIST_BRAND_NAME);
        }
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    class getBrands {

        @DisplayName("브랜드가 존재하면, 브랜드 목록을 조회할 수 있다.")
        @Test
        void returnsBrandList_whenBrandsExist() {
            // arrange
            createBrand(testRestTemplate,
                    new BrandDto.CreateBrandRequest(
                            "브랜드명1",
                            "https://example.com/logo1.png",
                            "브랜드 설명1"
                    ),
                    adminAuthHeaders()
            );
            createBrand(testRestTemplate,
                    new BrandDto.CreateBrandRequest(
                            "브랜드명2",
                            "https://example.com/logo2.png",
                            null
                    ),
                    adminAuthHeaders()
            );

            var url = UriComponentsBuilder.fromPath(BRAND_ADMIN_ENDPOINT)
                    .queryParam("page", 0)
                    .queryParam("size", 10)
                    .toUriString();

            // act
            var response = getBrandsRequest(url);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().content()).extracting(BrandResponse::name)
                            .containsExactly("브랜드명2", "브랜드명1"),
                    () -> assertThat(response.getBody().data().content()).extracting(BrandResponse::logoUrl)
                            .containsExactly("https://example.com/logo2.png", "https://example.com/logo1.png"),
                    () -> assertThat(response.getBody().data().content()).extracting(BrandResponse::description)
                            .containsExactly(null, "브랜드 설명1"),
                    () -> assertThat(response.getBody().data().content()).extracting(BrandResponse::createdAt)
                            .doesNotContainNull()
            );
        }

        @DisplayName("page/size 파라미터 없이 호출하면, 기본값으로 조회된다.")
        @Test
        void returnsDefaultPage_whenNoPageParams() {
            // arrange
            createBrand(testRestTemplate,
                    new BrandDto.CreateBrandRequest(
                            "브랜드명",
                            "https://example.com/logo.png",
                            null
                    ),
                    adminAuthHeaders()
            );

            // act
            var response = getBrandsRequest(BRAND_ADMIN_ENDPOINT);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().content()).hasSize(1)
            );
        }

        @DisplayName("페이지 크기보다 브랜드가 많으면, hasNext가 true이다.")
        @Test
        void returnsHasNextTrue_whenMoreBrandsExist() {
            // arrange
            createBrand(testRestTemplate,
                    new BrandDto.CreateBrandRequest(
                            "브랜드1",
                            "https://example.com/1.png",
                            null
                    ),
                    adminAuthHeaders()
            );
            createBrand(testRestTemplate,
                    new BrandDto.CreateBrandRequest(
                            "브랜드2",
                            "https://example.com/2.png",
                            null
                    ),
                    adminAuthHeaders()
            );
            createBrand(testRestTemplate,
                    new BrandDto.CreateBrandRequest(
                            "브랜드3",
                            "https://example.com/3.png",
                            null
                    ),
                    adminAuthHeaders()
            );

            var url = UriComponentsBuilder.fromPath(BRAND_ADMIN_ENDPOINT)
                    .queryParam("page", 0)
                    .queryParam("size", 2)
                    .toUriString();

            // act
            var response = getBrandsRequest(url);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().hasNext()).isTrue()
            );
        }

        @DisplayName("생성일 내림차순으로 정렬된다.")
        @Test
        void returnsBrandsSortedByCreatedAtDesc() {
            // arrange
            createBrand(testRestTemplate,
                    new BrandDto.CreateBrandRequest(
                            "첫번째",
                            "https://example.com/1.png",
                            null
                    ),
                    adminAuthHeaders()
            );
            createBrand(testRestTemplate,
                    new BrandDto.CreateBrandRequest(
                            "두번째",
                            "https://example.com/2.png",
                            null
                    ),
                    adminAuthHeaders()
            );
            createBrand(testRestTemplate,
                    new BrandDto.CreateBrandRequest(
                            "세번째",
                            "https://example.com/3.png",
                            null
                    ),
                    adminAuthHeaders()
            );

            var url = UriComponentsBuilder.fromPath(BRAND_ADMIN_ENDPOINT)
                    .queryParam("page", 0)
                    .queryParam("size", 10)
                    .toUriString();

            // act
            var response = getBrandsRequest(url);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().content()).extracting(BrandResponse::name)
                            .containsExactly("세번째", "두번째", "첫번째")
            );
        }

        @DisplayName("브랜드가 없으면, 빈 목록이 반환된다.")
        @Test
        void returnsEmptyList_whenNoBrandsExist() {
            // arrange
            var url = UriComponentsBuilder.fromPath(BRAND_ADMIN_ENDPOINT)
                    .queryParam("page", 0)
                    .queryParam("size", 10)
                    .toUriString();

            // act
            var response = getBrandsRequest(url);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().hasNext()).isFalse()
            );
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드 ID로 조회하면, 브랜드 정보를 반환한다.")
        @Test
        void returnsBrandDetails_whenBrandIdExists() {
            // arrange
            var result = createBrand(testRestTemplate,
                    new CreateBrandRequest(
                            "브랜드명",
                            "https://example.com/logo.png",
                            null
                    ),
                    adminAuthHeaders()
            );
            var brandId = result.getBody().data().brandId();

            // act
            ParameterizedTypeReference<ApiResponse<BrandResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            var response = testRestTemplate.exchange(
                    BRAND_ADMIN_ENDPOINT + "/" + brandId,
                    HttpMethod.GET,
                    new HttpEntity<>(adminAuthHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(brandId),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("브랜드명"),
                    () -> assertThat(response.getBody().data().logoUrl()).isEqualTo("https://example.com/logo.png"),
                    () -> assertThat(response.getBody().data().description()).isNull(),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull()
            );
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    class UpdateBrand {

        @DisplayName("존재하는 브랜드 ID로 업데이트하면, 브랜드 정보가 수정된다.")
        @Test
        void updatesBrand_whenBrandIdExists() {
            // arrange
            var result = createBrand(testRestTemplate,
                    new CreateBrandRequest(
                            "브랜드명",
                            "https://example.com/logo.png",
                            null
                    ),
                    adminAuthHeaders()
            );
            var brandId = result.getBody().data().brandId();

            var request = new BrandDto.UpdateBrandRequest("수정된 브랜드명", "https://example.com/updated-logo.png", "수정된 설명");

            // act
            ParameterizedTypeReference<ApiResponse<BrandResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            testRestTemplate.exchange(
                    BRAND_ADMIN_ENDPOINT + "/" + brandId,
                    HttpMethod.PUT,
                    new HttpEntity<>(request, adminAuthHeaders()),
                    responseType
            );

            // assert
            var getResponse = testRestTemplate.exchange(
                    BRAND_ADMIN_ENDPOINT + "/" + brandId,
                    HttpMethod.GET,
                    new HttpEntity<>(adminAuthHeaders()),
                    responseType
            );
            assertAll(
                    () -> assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(getResponse.getBody()).isNotNull(),
                    () -> assertThat(getResponse.getBody().data().id()).isEqualTo(brandId),
                    () -> assertThat(getResponse.getBody().data().name()).isEqualTo("수정된 브랜드명"),
                    () -> assertThat(getResponse.getBody().data().logoUrl()).isEqualTo(
                            "https://example.com/updated-logo.png"),
                    () -> assertThat(getResponse.getBody().data().description()).isEqualTo("수정된 설명"),
                    () -> assertThat(getResponse.getBody().data().createdAt()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID로 수정하면, 404 응답을 받는다.")
        @Test
        void returnsNotFound_whenBrandIdDoesNotExist() {
            // arrange
            var request = new BrandDto.UpdateBrandRequest("브랜드명", "https://example.com/logo.png", "설명");

            // act
            var response = updateBrandRequest(999L, request);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.BRAND_NOT_FOUND);
        }

        @DisplayName("다른 활성 브랜드와 동일한 이름으로 수정하면, 400 응답을 받는다.")
        @Test
        void returnsBadRequest_whenDuplicateNameExists() {
            // arrange
            createBrand(testRestTemplate, new CreateBrandRequest("기존브랜드", "https://example.com/logo1.png", null),
                    adminAuthHeaders());
            var result = createBrand(testRestTemplate,
                    new CreateBrandRequest("내브랜드", "https://example.com/logo2.png", null), adminAuthHeaders());
            var brandId = result.getBody().data().brandId();

            var request = new BrandDto.UpdateBrandRequest("기존브랜드", "https://example.com/logo2.png", null);

            // act
            var response = updateBrandRequest(brandId, request);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.ALREADY_EXIST_BRAND_NAME);
        }

        @DisplayName("브랜드명이 빈 값이면, 400 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            var result = createBrand(testRestTemplate,
                    new CreateBrandRequest("브랜드명", "https://example.com/logo.png", null), adminAuthHeaders());
            var brandId = result.getBody().data().brandId();

            var request = new BrandDto.UpdateBrandRequest("", "https://example.com/logo.png", null);

            // act
            var response = updateBrandRequest(brandId, request);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST);
        }

        @DisplayName("브랜드명이 50자 초과이면, INVALID_BRAND_NAME 에러 응답을 받는다.")
        @Test
        void returnsInvalidBrandName_whenNameIsTooLong() {
            // arrange
            var result = createBrand(testRestTemplate,
                    new CreateBrandRequest("브랜드명", "https://example.com/logo.png", null), adminAuthHeaders());
            var brandId = result.getBody().data().brandId();

            var name = "a".repeat(51);
            var request = new BrandDto.UpdateBrandRequest(name, "https://example.com/logo.png", null);

            // act
            var response = updateBrandRequest(brandId, request);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_BRAND_NAME);
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    class DeleteBrand {

        @DisplayName("유효한 브랜드를 삭제하면, 200 성공 응답을 받는다.")
        @Test
        void deletesBrand_whenValidBrandId() {
            // arrange
            var brandId = createBrand(testRestTemplate,
                    new CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));

            // act
            var response = BrandSteps.deleteBrand(testRestTemplate, brandId);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("존재하지 않는 브랜드를 삭제하면, 404 응답을 받는다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // act
            var response = BrandSteps.deleteBrand(testRestTemplate, 999L);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.BRAND_NOT_FOUND);
        }

        @DisplayName("이미 삭제된 브랜드를 삭제하면, 400 응답을 받는다.")
        @Test
        void returnsBadRequest_whenBrandIsAlreadyDeleted() {
            // arrange
            var brandId = createBrand(testRestTemplate,
                    new CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            BrandSteps.deleteBrand(testRestTemplate, brandId);

            // act
            var response = BrandSteps.deleteBrand(testRestTemplate, brandId);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.ALREADY_DELETED_BRAND);
        }
    }

    private ResponseEntity<ApiResponse<PageResponse<BrandResponse>>> getBrandsRequest(String url) {
        ParameterizedTypeReference<ApiResponse<PageResponse<BrandResponse>>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(adminAuthHeaders()),
                responseType
        );
    }

    private ResponseEntity<ApiResponse<Object>> updateBrandRequest(Long brandId, BrandDto.UpdateBrandRequest request) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                BRAND_ADMIN_ENDPOINT + "/" + brandId,
                HttpMethod.PUT,
                new HttpEntity<>(request, adminAuthHeaders()),
                responseType
        );
    }
}