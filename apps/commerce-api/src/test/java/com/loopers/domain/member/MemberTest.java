package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import com.loopers.domain.member.vo.Phone;
import com.loopers.domain.member.vo.MemberId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MemberTest {

    @Nested
    @DisplayName("Member 생성")
    class MemberCreationTest {

        @Test
        @DisplayName("Member 생성 성공")
        void createMemberSuccess() {
            // given
            MemberId memberId = mock(MemberId.class);
            Password password = mock(Password.class);
            Name name = mock(Name.class);
            Email email = mock(Email.class);
            BirthDate birthDate = mock(BirthDate.class);
            Phone phone = mock(Phone.class);

            when(password.value()).thenReturn("1Q2w3e4r!");
            when(birthDate.toYymmdd()).thenReturn("990115");
            when(birthDate.toMmdd()).thenReturn("0115");
            when(birthDate.toDdmm()).thenReturn("1501");

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new Member(memberId, password, name, email, birthDate, phone));
        }
    }

    @Nested
    @DisplayName("비밀번호 생년월일 포함 검증")
    class PasswordBirthDateValidationTest {

        @Test
        @DisplayName("비밀번호에 생년월일(YYMMDD) 포함 시 실패")
        void passwordContainsYymmdd() {
            // given
            MemberId memberId = new MemberId("testmember");
            Password password = new Password("Qw990115!");
            Name name = new Name("홍길동");
            Email email = new Email("test@example.com");
            BirthDate birthDate = BirthDate.of("19990115");
            Phone phone = new Phone("010-1234-5678");

            // when & then
            assertThatThrownBy(() -> new Member(memberId, password, name, email, birthDate, phone)).isInstanceOf(CoreException.class)
                    .hasMessage("비밀번호에 생년월일을 포함할 수 없습니다");
        }

        @Test
        @DisplayName("비밀번호에 생년월일(MMDD) 포함 시 실패")
        void passwordContainsMmdd() {
            // given
            MemberId memberId = new MemberId("testmember");
            Password password = new Password("1Qwer0115!");
            Name name = new Name("홍길동");
            Email email = new Email("test@example.com");
            BirthDate birthDate = BirthDate.of("19990115");
            Phone phone = new Phone("010-1234-5678");

            // when & then
            assertThatThrownBy(() -> new Member(memberId, password, name, email, birthDate, phone)).isInstanceOf(CoreException.class)
                    .hasMessage("비밀번호에 생년월일을 포함할 수 없습니다");
        }

        @Test
        @DisplayName("비밀번호에 생년월일(DDMM) 포함 시 실패")
        void passwordContainsDdmm() {
            // given
            MemberId memberId = new MemberId("testmember");
            Password password = new Password("1Qwer1501!");
            Name name = new Name("홍길동");
            Email email = new Email("test@example.com");
            BirthDate birthDate = BirthDate.of("19990115");
            Phone phone = new Phone("010-1234-5678");

            // when & then
            assertThatThrownBy(() -> new Member(memberId, password, name, email, birthDate, phone)).isInstanceOf(CoreException.class)
                    .hasMessage("비밀번호에 생년월일을 포함할 수 없습니다");
        }

        @Test
        @DisplayName("비밀번호에 생년월일 미포함 시 성공")
        void passwordNotContainsBirthDate() {
            // given
            MemberId memberId = new MemberId("testmember");
            Password password = new Password("1Q2w3e4r!");
            Name name = new Name("홍길동");
            Email email = new Email("test@example.com");
            BirthDate birthDate = BirthDate.of("19990115");
            Phone phone = new Phone("010-1234-5678");

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new Member(memberId, password, name, email, birthDate, phone));
        }

        @Test
        @DisplayName("인코딩된 비밀번호는 생년월일 포함 검증을 생략한다")
        void skipBirthDateValidationWhenPasswordEncoded() {
            // given
            MemberId memberId = new MemberId("testmember");
            Password password = Password.ofEncoded("$2a$10$encodedPassword");
            Name name = new Name("홍길동");
            Email email = new Email("test@example.com");
            BirthDate birthDate = BirthDate.of("19990115");
            Phone phone = new Phone("010-1234-5678");

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new Member(memberId, password, name, email, birthDate, phone));
        }
    }

    @Nested
    @DisplayName("이름 마스킹")
    class MaskingTest {

        @Test
        @DisplayName("한글 이름 마스킹 - 3글자")
        void getMaskedKoreanName() {
            // given
            MemberId memberId = new MemberId("testmember");
            Password password = new Password("1Q2w3e4r!");
            Name name = new Name("홍길동");
            Email email = new Email("test@example.com");
            BirthDate birthDate = new BirthDate(LocalDate.of(1999, 1, 15));
            Phone phone = new Phone("010-1234-5678");

            Member member = new Member(memberId, password, name, email, birthDate, phone);

            // when
            String maskedName = member.getMaskedName();

            // then
            assertThat(maskedName).isEqualTo("홍길*");
        }

        @Test
        @DisplayName("영문 이름 마스킹")
        void getMaskedEnglishName() {
            // given
            MemberId memberId = new MemberId("testmember");
            Password password = new Password("1Q2w3e4r!");
            Name name = new Name("John");
            Email email = new Email("test@example.com");
            BirthDate birthDate = new BirthDate(LocalDate.of(1999, 1, 15));
            Phone phone = new Phone("010-1234-5678");

            Member member = new Member(memberId, password, name, email, birthDate, phone);

            // when
            String maskedName = member.getMaskedName();

            // then
            assertThat(maskedName).isEqualTo("Joh*");
        }

        @Test
        @DisplayName("한 글자 이름 마스킹")
        void getMaskedSingleCharName() {
            // given
            MemberId memberId = new MemberId("testmember");
            Password password = new Password("1Q2w3e4r!");
            Name name = new Name("김");
            Email email = new Email("test@example.com");
            BirthDate birthDate = new BirthDate(LocalDate.of(1999, 1, 15));
            Phone phone = new Phone("010-1234-5678");

            Member member = new Member(memberId, password, name, email, birthDate, phone);

            // when
            String maskedName = member.getMaskedName();

            // then
            assertThat(maskedName).isEqualTo("*");
        }

        @Test
        @DisplayName("두 글자 이름 마스킹")
        void getMaskedTwoCharName() {
            // given
            MemberId memberId = new MemberId("testmember");
            Password password = new Password("1Q2w3e4r!");
            Name name = new Name("홍길");
            Email email = new Email("test@example.com");
            BirthDate birthDate = new BirthDate(LocalDate.of(1999, 1, 15));
            Phone phone = new Phone("010-1234-5678");

            Member member = new Member(memberId, password, name, email, birthDate, phone);

            // when
            String maskedName = member.getMaskedName();

            // then
            assertThat(maskedName).isEqualTo("홍*");
        }
    }
}
