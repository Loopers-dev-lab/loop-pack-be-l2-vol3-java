package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.E2ETestFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(E2ETestFixture.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BrandApiE2ETest {

    private static final String ENDPOINT = "/api/v1/brands";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private E2ETestFixture fixture;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 브랜드_목록_조회 {

        @Test
        void 조건_없이_조회하면_활성_브랜드만_이름_오름차순으로_페이징하여_200_응답한다() {
            fixture.registerBrand("다나이키", "스포츠 브랜드");
            fixture.registerBrand("가아디다스", "독일 스포츠 브랜드");
            fixture.registerBrand("나뉴발란스", "미국 스포츠 브랜드");

            ResponseEntity<ApiResponse<PageResponse<BrandV1Dto.BrandResponse>>> response = getList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(3),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("가아디다스"),
                    () -> assertThat(response.getBody().data().content().get(1).name()).isEqualTo("나뉴발란스"),
                    () -> assertThat(response.getBody().data().content().get(2).name()).isEqualTo("다나이키"),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(3),
                    () -> assertThat(response.getBody().data().page()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().size()).isEqualTo(20)
            );
        }

        @Test
        void 삭제된_브랜드는_반환하지_않는다() {
            fixture.registerBrand("나이키", "스포츠 브랜드");
            Long deletedBrandId = fixture.registerBrand("아디다스", "독일 스포츠 브랜드");
            fixture.deleteBrand(deletedBrandId);

            ResponseEntity<ApiResponse<PageResponse<BrandV1Dto.BrandResponse>>> response = getList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("나이키")
            );
        }

        @Test
        void name_키워드로_검색하면_해당_키워드가_포함된_활성_브랜드만_반환한다() {
            fixture.registerBrand("나이키", "스포츠 브랜드");
            fixture.registerBrand("아디다스", "독일 스포츠 브랜드");
            fixture.registerBrand("뉴발란스", "미국 스포츠 브랜드");

            ResponseEntity<ApiResponse<PageResponse<BrandV1Dto.BrandResponse>>> response =
                    getList("?name=나이키");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("나이키")
            );
        }

        @Test
        void 결과가_없으면_빈_목록을_반환한다() {
            ResponseEntity<ApiResponse<PageResponse<BrandV1Dto.BrandResponse>>> response =
                    getList("?name=존재하지않는브랜드");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0)
            );
        }

        @Test
        void 요청_필드_규칙_위반_시_400_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?page=-1", HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class 브랜드_상세_조회 {

        @Test
        void 활성_브랜드를_조회하면_200_응답과_브랜드_정보를_반환한다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");

            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = getDetail(brandId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(brandId),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("스포츠 브랜드"),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().updatedAt()).isNotNull()
            );
        }

        @Test
        void 삭제된_브랜드를_조회하면_404_응답() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            fixture.deleteBrand(brandId);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + brandId, HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 미존재_브랜드를_조회하면_404_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/999", HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // --- 헬퍼 메서드 ---

    private ResponseEntity<ApiResponse<PageResponse<BrandV1Dto.BrandResponse>>> getList(String queryString) {
        return testRestTemplate.exchange(
                ENDPOINT + queryString, HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> getDetail(Long brandId) {
        return testRestTemplate.exchange(
                ENDPOINT + "/" + brandId, HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
    }
}
