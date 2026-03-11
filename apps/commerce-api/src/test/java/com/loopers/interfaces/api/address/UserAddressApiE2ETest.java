package com.loopers.interfaces.api.address;

import com.loopers.domain.address.UserAddress;
import com.loopers.domain.address.UserAddressRepository;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserAddressApiE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserAddressRepository userAddressRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @BeforeEach
    void setUp() {
        UserRequest.SignupRequest signupRequest = new UserRequest.SignupRequest(
                "testuser", "Hx7!mK2@", "테스터", "1994-11-15", "test@example.com");
        testRestTemplate.postForEntity("/api/v1/users", signupRequest, ApiResponse.class);
        // userId는 첫 번째 생성된 사용자이므로 1L
        userId = 1L;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "testuser");
        headers.set("X-Loopers-LoginPw", "Hx7!mK2@");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private UserAddressRequest.RegisterAddressRequest createRegisterRequest(String receiverName) {
        return new UserAddressRequest.RegisterAddressRequest(
                receiverName, "010-1234-5678", "12345", "서울시 강남구", "4층 401호");
    }

    private ResponseEntity<ApiResponse> registerAddress(String receiverName) {
        return testRestTemplate.exchange(
                "/api/v1/users/me/addresses", HttpMethod.POST,
                new HttpEntity<>(createRegisterRequest(receiverName), authHeaders()),
                ApiResponse.class);
    }

    @DisplayName("GET /api/v1/users/me/addresses")
    @Nested
    class 배송지_목록_조회 {

        @Test
        void 배송지_목록_조회에_성공하면_200_OK를_반환한다() {
            // arrange
            registerAddress("홍길동");

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/addresses", HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 배송지가_없으면_빈_목록을_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/addresses", HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 인증_없이_요청하면_401_Unauthorized를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/addresses", HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("POST /api/v1/users/me/addresses")
    @Nested
    class 배송지_등록 {

        @Test
        void 등록에_성공하면_200_OK를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = registerAddress("홍길동");

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 첫_번째_배송지는_기본주소로_설정된다() {
            // act
            registerAddress("홍길동");

            // assert
            List<UserAddress> addresses = userAddressRepository.findAllByUserIdAndDeletedAtIsNull(userId);
            assertThat(addresses).hasSize(1);
            assertThat(addresses.get(0).isDefault()).isTrue();
        }

        @Test
        void 두_번째_배송지는_기본주소가_아니다() {
            // arrange
            registerAddress("홍길동");

            // act
            registerAddress("김철수");

            // assert
            List<UserAddress> addresses = userAddressRepository.findAllByUserIdAndDeletedAtIsNull(userId);
            long defaultCount = addresses.stream().filter(UserAddress::isDefault).count();
            assertThat(defaultCount).isEqualTo(1);
        }

        @Test
        void 인증_없이_요청하면_401_Unauthorized를_반환한다() {
            // act
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/addresses", HttpMethod.POST,
                    new HttpEntity<>(createRegisterRequest("홍길동"), headers),
                    ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PUT /api/v1/users/me/addresses/{addressId}")
    @Nested
    class 배송지_수정 {

        @Test
        void 수정에_성공하면_200_OK를_반환한다() {
            // arrange
            registerAddress("홍길동");
            UserAddress address = userAddressRepository.findAllByUserIdAndDeletedAtIsNull(userId).get(0);

            // act
            UserAddressRequest.UpdateAddressRequest updateRequest =
                    new UserAddressRequest.UpdateAddressRequest("김철수", "010-9876-5432", "54321", "부산시", "3층");
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/addresses/" + address.getId(), HttpMethod.PATCH,
                    new HttpEntity<>(updateRequest, authHeaders()),
                    ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 존재하지_않는_배송지면_404_Not_Found를_반환한다() {
            // act
            UserAddressRequest.UpdateAddressRequest updateRequest =
                    new UserAddressRequest.UpdateAddressRequest("김철수", "010-9876-5432", "54321", "부산시", null);
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/addresses/999", HttpMethod.PATCH,
                    new HttpEntity<>(updateRequest, authHeaders()),
                    ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 타인의_배송지면_403_Forbidden을_반환한다() {
            // arrange - 다른 사용자의 배송지 직접 생성
            UserAddress otherAddress = userAddressRepository.save(
                    UserAddress.register(999L, "타인", "010-0000-0000", "00000", "어딘가", null));

            // act
            UserAddressRequest.UpdateAddressRequest updateRequest =
                    new UserAddressRequest.UpdateAddressRequest("김철수", "010-9876-5432", "54321", "부산시", null);
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/addresses/" + otherAddress.getId(), HttpMethod.PATCH,
                    new HttpEntity<>(updateRequest, authHeaders()),
                    ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @DisplayName("DELETE /api/v1/users/me/addresses/{addressId}")
    @Nested
    class 배송지_삭제 {

        @Test
        void 삭제에_성공하면_200_OK를_반환한다() {
            // arrange
            registerAddress("홍길동");
            UserAddress address = userAddressRepository.findAllByUserIdAndDeletedAtIsNull(userId).get(0);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/addresses/" + address.getId(), HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 삭제_후_목록에서_조회되지_않는다() {
            // arrange
            registerAddress("홍길동");
            UserAddress address = userAddressRepository.findAllByUserIdAndDeletedAtIsNull(userId).get(0);

            // act
            testRestTemplate.exchange(
                    "/api/v1/users/me/addresses/" + address.getId(), HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(userAddressRepository.findById(address.getId())).isEmpty();
        }

        @Test
        void 기본주소_삭제_시_다른_주소가_기본주소로_전환된다() {
            // arrange
            registerAddress("홍길동");
            registerAddress("김철수");
            List<UserAddress> addresses = userAddressRepository.findAllByUserIdAndDeletedAtIsNull(userId);
            UserAddress defaultAddr = addresses.stream().filter(UserAddress::isDefault).findFirst().orElseThrow();

            // act
            testRestTemplate.exchange(
                    "/api/v1/users/me/addresses/" + defaultAddr.getId(), HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            List<UserAddress> remaining = userAddressRepository.findAllByUserIdAndDeletedAtIsNull(userId);
            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).isDefault()).isTrue();
        }

        @Test
        void 존재하지_않는_배송지면_404_Not_Found를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/addresses/999", HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 타인의_배송지면_403_Forbidden을_반환한다() {
            // arrange
            UserAddress otherAddress = userAddressRepository.save(
                    UserAddress.register(999L, "타인", "010-0000-0000", "00000", "어딘가", null));

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/addresses/" + otherAddress.getId(), HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
