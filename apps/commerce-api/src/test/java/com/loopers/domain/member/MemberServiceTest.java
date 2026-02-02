package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository);
    }

    @DisplayName("회원 가입")
    @Nested
    class Register {

        @DisplayName("이미 존재하는 로그인 ID로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // Arrange
            String loginId = "testuser";
            given(memberRepository.findByLoginId(loginId))
                .willReturn(Optional.of(
                    new Member(loginId, "encrypted", "홍길동", "19900101", "test@example.com")
                ));

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                memberService.register(loginId, "encrypted", "김철수", "19950505", "new@example.com")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("내정보 조회")
    @Nested
    class GetMember {

        @DisplayName("존재하는 회원의 loginId로 조회하면, 회원 정보를 반환한다.")
        @Test
        void returnsMember_whenLoginIdExists() {
            // Arrange
            String loginId = "testuser";
            Member member = new Member(loginId, "encrypted", "홍길동", "19900101", "test@example.com");
            given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));

            // Act
            Member result = memberService.getMember(loginId);

            // Assert
            assertAll(
                () -> assertThat(result.getLoginId()).isEqualTo("testuser"),
                () -> assertThat(result.getName()).isEqualTo("홍길동"),
                () -> assertThat(result.getBirthday()).isEqualTo("19900101"),
                () -> assertThat(result.getEmail()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("존재하지 않는 loginId로 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLoginIdDoesNotExist() {
            // Arrange
            String loginId = "nouser";
            given(memberRepository.findByLoginId(loginId)).willReturn(Optional.empty());

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                memberService.getMember(loginId)
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
