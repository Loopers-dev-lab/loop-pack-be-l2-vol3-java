package com.loopers.domain.user;

import com.loopers.domain.BaseStringIdEntity;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UserModel 도메인 모델 테스트")
class UserModelTest {

    // === 생성 검증 ===

    @Nested
    @DisplayName("생성 검증")
    class CreateTests {

        @Test
        @DisplayName("유효한 입력으로 생성 성공")
        void create_WithValidInputs_ShouldSuccess() {
            UserModel user = UserModel.createWithEncodedPassword(
                    "testuser01", "{bcrypt}encoded", "홍길동", "19900101", "test@example.com", "서울시 강남구"
            );

            assertThat(user.getLoginId()).isEqualTo("testuser01");
            assertThat(user.getPassword()).isEqualTo("{bcrypt}encoded");
            assertThat(user.getUserName()).isEqualTo("홍길동");
            assertThat(user.getBirthday()).isEqualTo("19900101");
            assertThat(user.getEmail()).isEqualTo("test@example.com");
            assertThat(user.getAddress()).isEqualTo("서울시 강남구");
        }

        @Test
        @DisplayName("loginId가 null이면 CoreException 발생")
        void create_WithNullLoginId_ShouldThrowCoreException() {
            assertThatThrownBy(() -> UserModel.createWithEncodedPassword(
                    null, "{bcrypt}pw", "홍길동", "19900101", "a@b.com", "서울"
            )).isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("loginId가 빈 문자열이면 CoreException 발생")
        void create_WithBlankLoginId_ShouldThrowCoreException() {
            assertThatThrownBy(() -> UserModel.createWithEncodedPassword(
                    "  ", "{bcrypt}pw", "홍길동", "19900101", "a@b.com", "서울"
            )).isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("loginId에 특수문자가 포함되면 CoreException 발생")
        void create_WithNonAlphanumericLoginId_ShouldThrowCoreException() {
            assertThatThrownBy(() -> UserModel.createWithEncodedPassword(
                    "test!user", "{bcrypt}pw", "홍길동", "19900101", "a@b.com", "서울"
            )).isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("생성 시 del_yn 기본값은 'N'이다")
        void create_DefaultDelYn_ShouldBeN() {
            UserModel user = createTestUser("홍길동");
            assertThat(user.getDelYn()).isEqualTo("N");
        }

        @Test
        @DisplayName("BaseStringIdEntity를 상속한다")
        void create_ShouldExtendBaseStringIdEntity() {
            UserModel user = createTestUser("홍길동");
            assertThat(user).isInstanceOf(BaseStringIdEntity.class);
        }
    }

    // === 비밀번호 검증 ===

    @Nested
    @DisplayName("비밀번호 검증")
    class PasswordValidationTests {

        @Test
        @DisplayName("유효한 비밀번호는 예외가 발생하지 않는다")
        void validatePassword_WithValidPassword_ShouldNotThrow() {
            assertThatCode(() ->
                    UserModel.validatePassword("Test1234!@#", "19900101")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("8자 미만 비밀번호는 CoreException 발생")
        void validatePassword_WithTooShort_ShouldThrow() {
            assertThatThrownBy(() ->
                    UserModel.validatePassword("Short1!", "19900101")
            ).isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("16자 초과 비밀번호는 CoreException 발생")
        void validatePassword_WithTooLong_ShouldThrow() {
            assertThatThrownBy(() ->
                    UserModel.validatePassword("VeryLongPassword1234!@#", "19900101")
            ).isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("생년월일이 포함된 비밀번호는 CoreException 발생")
        void validatePassword_ContainingBirthday_ShouldThrow() {
            assertThatThrownBy(() ->
                    UserModel.validatePassword("a19900101b!", "19900101")
            ).isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("허용되지 않은 문자가 포함된 비밀번호는 CoreException 발생")
        void validatePassword_WithInvalidChars_ShouldThrow() {
            assertThatThrownBy(() ->
                    UserModel.validatePassword("Test1234 공백", "19900101")
            ).isInstanceOf(CoreException.class);
        }
    }

    // === 이름 마스킹 ===

    @Nested
    @DisplayName("이름 마스킹")
    class MaskingTests {

        @Test
        @DisplayName("이름 마지막 글자가 *로 마스킹된다")
        void getMaskedName_ShouldMaskLastChar() {
            UserModel user = createTestUser("홍길동");
            assertThat(user.getMaskedName()).isEqualTo("홍길*");
        }

        @Test
        @DisplayName("1글자 이름은 *로 반환된다")
        void getMaskedName_SingleChar_ShouldReturnAsterisk() {
            UserModel user = createTestUser("홍");
            assertThat(user.getMaskedName()).isEqualTo("*");
        }
    }

    // === 비밀번호 업데이트 ===

    @Nested
    @DisplayName("비밀번호 업데이트")
    class UpdatePasswordTests {

        @Test
        @DisplayName("유효한 암호화 비밀번호로 업데이트 성공")
        void updatePassword_WithValidEncodedPassword_ShouldUpdate() {
            UserModel user = createTestUser("홍길동");
            user.updatePassword("{bcrypt}newencoded");
            assertThat(user.getPassword()).isEqualTo("{bcrypt}newencoded");
        }

        @Test
        @DisplayName("빈 비밀번호로 업데이트 시 CoreException 발생")
        void updatePassword_WithBlank_ShouldThrow() {
            UserModel user = createTestUser("홍길동");
            assertThatThrownBy(() -> user.updatePassword(""))
                    .isInstanceOf(CoreException.class);
        }
    }

    // === 소프트 삭제 ===

    @Nested
    @DisplayName("소프트 삭제")
    class SoftDeleteTests {

        @Test
        @DisplayName("softDelete 호출 시 del_yn='Y', deletedAt 설정")
        void softDelete_ShouldSetDelYnYAndDeletedAt() {
            UserModel user = createTestUser("홍길동");
            user.softDelete();

            assertThat(user.getDelYn()).isEqualTo("Y");
            assertThat(user.getDeletedAt()).isNotNull();
            assertThat(user.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("이미 삭제된 상태에서 softDelete는 멱등하다")
        void softDelete_WhenAlreadyDeleted_ShouldBeIdempotent() {
            UserModel user = createTestUser("홍길동");
            user.softDelete();
            var firstDeletedAt = user.getDeletedAt();

            user.softDelete();
            assertThat(user.getDeletedAt()).isEqualTo(firstDeletedAt);
        }

        @Test
        @DisplayName("restore 호출 시 del_yn='N', deletedAt=null")
        void restore_ShouldSetDelYnNAndClearDeletedAt() {
            UserModel user = createTestUser("홍길동");
            user.softDelete();
            user.restore();

            assertThat(user.getDelYn()).isEqualTo("N");
            assertThat(user.getDeletedAt()).isNull();
            assertThat(user.isDeleted()).isFalse();
        }
    }

    // === Helper ===

    private UserModel createTestUser(String userName) {
        return UserModel.createWithEncodedPassword(
                "test01", "{bcrypt}pw", userName, "19900101", "a@b.com", "서울"
        );
    }
}
