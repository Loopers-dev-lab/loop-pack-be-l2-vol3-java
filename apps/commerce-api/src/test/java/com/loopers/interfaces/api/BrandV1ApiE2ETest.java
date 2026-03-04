package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.interfaces.api.brand.dto.BrandV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/brands";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandJpaRepository brandJpaRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("존재하는 브랜드를 조회하면, 200 OK 응답을 반환한다.")
    @Test
    void returnsOk_whenBrandExists() {
        // arrange
        Brand saved = brandJpaRepository.save(Brand.create("TEST_BRAND", "스포츠 브랜드"));

        // act
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/" + saved.getId(), HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        // assert
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody().data().name()).isEqualTo("TEST_BRAND"),
            () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId())
        );
    }

    @DisplayName("존재하지 않는 브랜드를 조회하면, 404 Not Found 응답을 반환한다.")
    @Test
    void returnsNotFound_whenBrandNotExists() {
        // act
        ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT + "/999", HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @DisplayName("삭제된 브랜드를 조회하면, 404 Not Found 응답을 반환한다.")
    @Test
    void returnsNotFound_whenBrandIsDeleted() {
        // arrange
        Brand saved = brandJpaRepository.save(Brand.create("TEST_BRAND", "스포츠 브랜드"));
        saved.delete();
        brandJpaRepository.save(saved);

        // act
        ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT + "/" + saved.getId(), HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
