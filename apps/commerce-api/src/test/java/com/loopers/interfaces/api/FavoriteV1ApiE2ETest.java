package com.loopers.interfaces.api;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.model.BrandCommand;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.model.MemberCommand;
import com.loopers.domain.member.service.PasswordEncryptor;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductCommand;
import com.loopers.infrastructure.brand.entity.BrandEntity;
import com.loopers.infrastructure.brand.repository.BrandJpaRepository;
import com.loopers.infrastructure.favorite.entity.FavoriteEntity;
import com.loopers.infrastructure.favorite.repository.FavoriteJpaRepository;
import com.loopers.infrastructure.member.entity.MemberEntity;
import com.loopers.infrastructure.member.repository.MemberJpaRepository;
import com.loopers.infrastructure.product.entity.ProductEntity;
import com.loopers.infrastructure.product.repository.ProductJpaRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FavoriteV1ApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private static final String ENDPOINT_FAVORITES = "/api/v1/members/me/favorites";

    private final TestRestTemplate testRestTemplate;
    private final MemberJpaRepository memberJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final FavoriteJpaRepository favoriteJpaRepository;
    private final PasswordEncryptor passwordEncryptor;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public FavoriteV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        MemberJpaRepository memberJpaRepository,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        FavoriteJpaRepository favoriteJpaRepository,
        PasswordEncryptor passwordEncryptor,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.memberJpaRepository = memberJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.favoriteJpaRepository = favoriteJpaRepository;
        this.passwordEncryptor = passwordEncryptor;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders createAuthHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, password);
        return headers;
    }

    private MemberEntity saveMember(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        MemberCommand.SignUp command = new MemberCommand.SignUp(loginId, rawPassword, name, birthDate, email);
        Member model = Member.signUp(command, passwordEncryptor);
        return memberJpaRepository.save(MemberEntity.toEntity(model));
    }

    private BrandEntity saveBrand(String name, String description) {
        Brand brand = Brand.create(new BrandCommand.Create(name, description));
        return brandJpaRepository.save(BrandEntity.toEntity(brand));
    }

    private ProductEntity saveProduct(Long brandId, String name, int price, int stock) {
        Product product = Product.create(brandId, new ProductCommand.Create(brandId, name, price, stock));
        return productJpaRepository.save(ProductEntity.toEntity(product));
    }

    @DisplayName("POST /api/v1/members/me/favorites/{productId} - 좋아요 등록")
    @Nested
    class AddFavorite {

        @DisplayName("인증 헤더가 없으면 400 BAD_REQUEST 응답을 받는다")
        @Test
        void addFavorite_missingAuthHeaders() {
            // arrange
            BrandEntity brand = saveBrand("나이키", "나이키 설명");
            ProductEntity product = saveProduct(brand.getId(), "에어맥스", 150000, 10);

            HttpHeaders headers = new HttpHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_FAVORITES + "/" + product.getId(),
                HttpMethod.POST,
                new HttpEntity<>(headers),
                responseType
            );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }

        @DisplayName("잘못된 비밀번호로 요청하면 401 UNAUTHORIZED 응답을 받는다")
        @Test
        void addFavorite_wrongPassword() {
            // arrange
            saveMember("testuser", "Correct1234!", "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            BrandEntity brand = saveBrand("나이키", "나이키 설명");
            ProductEntity product = saveProduct(brand.getId(), "에어맥스", 150000, 10);

            HttpHeaders headers = createAuthHeaders("testuser", "WrongPass1!");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_FAVORITES + "/" + product.getId(),
                HttpMethod.POST,
                new HttpEntity<>(headers),
                responseType
            );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }

        @DisplayName("존재하지 않는 상품에 좋아요를 등록하면 404 NOT_FOUND 응답을 받는다")
        @Test
        void addFavorite_productNotFound() {
            // arrange
            saveMember("testuser", "Correct1234!", "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");

            HttpHeaders headers = createAuthHeaders("testuser", "Correct1234!");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_FAVORITES + "/99999",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                responseType
            );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요를 등록하면 409 CONFLICT 응답을 받는다")
        @Test
        void addFavorite_duplicateFavorite() {
            // arrange
            String rawPassword = "Correct1234!";
            MemberEntity member = saveMember("testuser", rawPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            BrandEntity brand = saveBrand("나이키", "나이키 설명");
            ProductEntity product = saveProduct(brand.getId(), "에어맥스", 150000, 10);

            HttpHeaders headers = createAuthHeaders("testuser", rawPassword);

            // 첫 번째 좋아요 등록
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(
                ENDPOINT_FAVORITES + "/" + product.getId(),
                HttpMethod.POST,
                new HttpEntity<>(headers),
                responseType
            );

            // act - 두 번째 좋아요 등록 (중복)
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_FAVORITES + "/" + product.getId(),
                HttpMethod.POST,
                new HttpEntity<>(headers),
                responseType
            );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
        }

        @DisplayName("올바른 인증 정보로 좋아요를 등록하면 성공하고 DB에 저장된다")
        @Test
        void addFavorite_success() {
            // arrange
            String rawPassword = "Correct1234!";
            MemberEntity member = saveMember("testuser", rawPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            BrandEntity brand = saveBrand("나이키", "나이키 설명");
            ProductEntity product = saveProduct(brand.getId(), "에어맥스", 150000, 10);

            HttpHeaders headers = createAuthHeaders("testuser", rawPassword);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_FAVORITES + "/" + product.getId(),
                HttpMethod.POST,
                new HttpEntity<>(headers),
                responseType
            );

            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());

            // DB 검증
            assertThat(favoriteJpaRepository.existsByMemberIdAndProductId(member.getId(), product.getId())).isTrue();
        }
    }

    @DisplayName("DELETE /api/v1/members/me/favorites/{productId} - 좋아요 취소")
    @Nested
    class DeleteFavorite {

        @DisplayName("인증 헤더가 없으면 400 BAD_REQUEST 응답을 받는다")
        @Test
        void deleteFavorite_missingAuthHeaders() {
            // arrange
            BrandEntity brand = saveBrand("나이키", "나이키 설명");
            ProductEntity product = saveProduct(brand.getId(), "에어맥스", 150000, 10);

            HttpHeaders headers = new HttpHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_FAVORITES + "/" + product.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                responseType
            );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }

        @DisplayName("잘못된 비밀번호로 요청하면 401 UNAUTHORIZED 응답을 받는다")
        @Test
        void deleteFavorite_wrongPassword() {
            // arrange
            String rawPassword = "Correct1234!";
            MemberEntity member = saveMember("testuser", rawPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            BrandEntity brand = saveBrand("나이키", "나이키 설명");
            ProductEntity product = saveProduct(brand.getId(), "에어맥스", 150000, 10);

            // 먼저 좋아요 등록
            HttpHeaders correctHeaders = createAuthHeaders("testuser", rawPassword);
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(
                ENDPOINT_FAVORITES + "/" + product.getId(),
                HttpMethod.POST,
                new HttpEntity<>(correctHeaders),
                responseType
            );

            HttpHeaders wrongHeaders = createAuthHeaders("testuser", "WrongPass1!");

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_FAVORITES + "/" + product.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(wrongHeaders),
                responseType
            );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }

        @DisplayName("좋아요하지 않은 상품을 취소하면 404 NOT_FOUND 응답을 받는다")
        @Test
        void deleteFavorite_notRegistered() {
            // arrange
            String rawPassword = "Correct1234!";
            saveMember("testuser", rawPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            BrandEntity brand = saveBrand("나이키", "나이키 설명");
            ProductEntity product = saveProduct(brand.getId(), "에어맥스", 150000, 10);

            HttpHeaders headers = createAuthHeaders("testuser", rawPassword);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_FAVORITES + "/" + product.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                responseType
            );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }

        @DisplayName("올바른 인증 정보로 좋아요를 취소하면 성공하고 DB에서 삭제된다")
        @Test
        void deleteFavorite_success() {
            // arrange
            String rawPassword = "Correct1234!";
            MemberEntity member = saveMember("testuser", rawPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            BrandEntity brand = saveBrand("나이키", "나이키 설명");
            ProductEntity product = saveProduct(brand.getId(), "에어맥스", 150000, 10);

            HttpHeaders headers = createAuthHeaders("testuser", rawPassword);

            // 먼저 좋아요 등록
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(
                ENDPOINT_FAVORITES + "/" + product.getId(),
                HttpMethod.POST,
                new HttpEntity<>(headers),
                responseType
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_FAVORITES + "/" + product.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                responseType
            );

            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());

            // DB 검증
            assertThat(favoriteJpaRepository.existsByMemberIdAndProductId(member.getId(), product.getId())).isFalse();
        }
    }
}
