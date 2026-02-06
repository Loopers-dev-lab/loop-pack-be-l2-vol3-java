package com.loopers.domain.user;

import com.loopers.application.user.UserInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입을 할 때, ")
    @Nested
    class SignUp {

        @DisplayName("중복된 사용자 ID로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void signUp_withDuplicateUserId_shouldThrowConflictException() {
            // given
            String duplicateUserId = "duplicate1";
            Email email1 = new Email("user1@example.com");
            BirthDate birthDate1 = new BirthDate("1990-01-15");
            Password password1 = Password.of("Pass1234!", birthDate1);
            Gender gender1 = Gender.MALE;

            userService.signUp(duplicateUserId, email1, birthDate1, password1, gender1);

            Email email2 = new Email("user2@example.com");
            BirthDate birthDate2 = new BirthDate("1995-05-20");
            Password password2 = Password.of("Pass5678!", birthDate2);
            Gender gender2 = Gender.FEMALE;

            // when & then
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.signUp(duplicateUserId, email2, birthDate2, password2, gender2);
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            assertThat(exception.getMessage()).contains("이미 존재하는 사용자 ID입니다");
        }

        @DisplayName("동시에 같은 ID로 가입하면, 1개만 성공하고 나머지는 실패한다.")
        @Test
        void signUp_withConcurrentSameUserId_shouldOnlyOneSucceed() throws InterruptedException {
            // given
            String userId = "concurrent";
            int threadCount = 5;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // when
            for (int i = 0; i < threadCount; i++) {
                int index = i;
                executorService.submit(() -> {
                    try {
                        Email email = new Email("user" + index + "@example.com");
                        BirthDate birthDate = new BirthDate("1990-01-15");
                        Password password = Password.of("Pass1234!", birthDate);
                        Gender gender = Gender.MALE;

                        userService.signUp(userId, email, birthDate, password, gender);
                        successCount.incrementAndGet();
                    } catch (CoreException e) {
                        if (e.getErrorType() == ErrorType.CONFLICT) {
                            failCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // then
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(threadCount - 1);
        }

        @DisplayName("성공 시 UserRepository.save()가 호출된다.")
        @Test
        void signUp_shouldCallRepositorySave() {
            // given
            String userId = "testuser1";
            Email email = new Email("test@example.com");
            BirthDate birthDate = new BirthDate("1990-01-15");
            Password password = Password.of("SecurePass1!", birthDate);
            Gender gender = Gender.MALE;

            // when
            UserModel savedUser = userService.signUp(userId, email, birthDate, password, gender);

            // then
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getId()).isNotNull();
            assertThat(savedUser.getUserId()).isEqualTo(userId);
        }

        @DisplayName("비밀번호가 암호화되어 저장된다.")
        @Test
        void signUp_shouldStoreEncryptedPassword() {
            // given
            String userId = "testuser2";
            String rawPassword = "RawPassword123!";
            Email email = new Email("test2@example.com");
            BirthDate birthDate = new BirthDate("1992-06-10");
            Password password = Password.of(rawPassword, birthDate);
            Gender gender = Gender.FEMALE;

            // when
            UserModel savedUser = userService.signUp(userId, email, birthDate, password, gender);

            // then
            assertThat(savedUser.getEncryptedPassword()).isNotEqualTo(rawPassword);
            assertThat(savedUser.getEncryptedPassword()).isNotBlank();
        }

        @DisplayName("암호화된 비밀번호는 BCrypt로 검증 가능하다.")
        @Test
        void signUp_shouldEncryptPasswordWithBCrypt() {
            // given
            String userId = "testuser3";
            String rawPassword = "VerifyPass456!";
            Email email = new Email("test3@example.com");
            BirthDate birthDate = new BirthDate("1988-12-25");
            Password password = Password.of(rawPassword, birthDate);
            Gender gender = Gender.MALE;

            // when
            UserModel savedUser = userService.signUp(userId, email, birthDate, password, gender);

            // then
            boolean matches = passwordEncoder.matches(rawPassword, savedUser.getEncryptedPassword());
            assertThat(matches).isTrue();
        }
    }

    @DisplayName("내 정보를 조회할 때, ")
    @Nested
    class GetMyInfo {

        @DisplayName("존재하지 않는 사용자 ID로 조회하면, null을 반환한다.")
        @Test
        void getMyInfo_withNonExistentUserId_shouldReturnNull() {
            // given
            String nonExistentUserId = "nouser";

            // when
            UserInfo userInfo = userService.getMyInfo(nonExistentUserId);

            // then
            assertThat(userInfo).isNull();
        }

        @DisplayName("존재하는 사용자 ID로 조회하면, 사용자 정보를 반환한다.")
        @Test
        void getMyInfo_withExistingUserId_shouldReturnUserInfo() {
            // given
            String userId = "testuser1";
            Email email = new Email("test@example.com");
            BirthDate birthDate = new BirthDate("1990-01-15");
            Password password = Password.of("SecurePass1!", birthDate);
            Gender gender = Gender.MALE;

            userService.signUp(userId, email, birthDate, password, gender);

            // when
            UserInfo userInfo = userService.getMyInfo(userId);

            // then
            assertThat(userInfo).isNotNull();
            assertThat(userInfo.userId()).isEqualTo(userId);
            assertThat(userInfo.email()).isEqualTo("test@example.com");
            assertThat(userInfo.birthDate()).isEqualTo("1990-01-15");
            assertThat(userInfo.gender()).isEqualTo("MALE");
        }

        @DisplayName("조회된 사용자 정보의 이름은 userId를 마스킹한 값이다.")
        @Test
        void getMyInfo_shouldReturnMaskedName() {
            // given
            String userId = "johnsmith";
            Email email = new Email("john@example.com");
            BirthDate birthDate = new BirthDate("1992-06-10");
            Password password = Password.of("Password2!", birthDate);
            Gender gender = Gender.MALE;

            userService.signUp(userId, email, birthDate, password, gender);

            // when
            UserInfo userInfo = userService.getMyInfo(userId);

            // then
            assertThat(userInfo.name()).isEqualTo("johnsmit*");
        }
    }
}
