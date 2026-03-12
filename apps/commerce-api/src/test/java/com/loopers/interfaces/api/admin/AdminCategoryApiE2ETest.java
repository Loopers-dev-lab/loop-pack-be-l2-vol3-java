package com.loopers.interfaces.api.admin;

import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.category.CategoryDto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class AdminCategoryApiE2ETest {

    private static final String ENDPOINT_CATEGORIES = "/api-admin/v1/categories";
    private static final String HEADER_LDAP = "X-Loopers-Ldap";
    private static final String LDAP_ADMIN = "loopers.admin";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final CategoryRepository categoryRepository;

    @Autowired
    AdminCategoryApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp,
            CategoryRepository categoryRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.categoryRepository = categoryRepository;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("GET /api-admin/v1/categories")
    class List {

        @Test
        @DisplayName("LDAP 헤더가 있으면 카테고리 목록을 페이지로 조회한다")
        void listCategories_withLdapHeader_returnsPagedList() {
            createCategory("푸드");
            createCategory("장난감");

            ResponseEntity<ApiResponse<CategoryDto.CategoryListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_CATEGORIES + "?page=0&size=20",
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data()).isNotNull();
            assertThat(response.getBody().data().page()).isEqualTo(0);
            assertThat(response.getBody().data().size()).isEqualTo(20);
            assertThat(response.getBody().data().totalElements()).isEqualTo(2);
            assertThat(response.getBody().data().items()).hasSize(2);
        }

        @Test
        @DisplayName("LDAP 헤더가 없으면 401을 반환한다")
        void listCategories_withoutLdapHeader_returnsUnauthorized() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT_CATEGORIES + "?page=0&size=20",
                    HttpMethod.GET,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("GET /api-admin/v1/categories/{categoryId}")
    class Detail {

        @Test
        @DisplayName("존재하는 카테고리를 상세 조회한다")
        void getCategory_whenExists_returnsOk() {
            UUID categoryId = createCategory("리빙");

            ResponseEntity<ApiResponse<CategoryDto.CategoryResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_CATEGORIES + "/" + categoryId,
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data()).isNotNull();
            assertThat(response.getBody().data().id()).isEqualTo(categoryId);
            assertThat(response.getBody().data().name()).isEqualTo("리빙");
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 ID 조회 시 400을 반환한다")
        void getCategory_whenNotExists_returnsBadRequest() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT_CATEGORIES + "/999999",
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LDAP, LDAP_ADMIN);
        return headers;
    }

    private UUID createCategory(String name) {
        return categoryRepository.save(new Category(name)).id();
    }
}
