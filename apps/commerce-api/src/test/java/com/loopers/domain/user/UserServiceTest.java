package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String VALID_LOGIN_ID = "namjin123";
    private static final String VALID_PASSWORD = "qwer@1234";
    private static final String VALID_NAME = "namjin";
    private static final String VALID_BIRTHDAY = "1994-05-25";
    private static final String VALID_EMAIL = "epemxksl@gmail.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @DisplayName("회원가입을 할 때, ")
    @Nested
    class Signup {

        @DisplayName("정상적인 정보로 회원가입이 성공한다.")
        @Test
        void signupSucceeds_whenInfoIsValid() {
            // arrange
            SignupCommand command = new SignupCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // stub: findByLoginId 호출하면 빈 값 반환 (해당 아이디로 가입된 회원 없음)
            when(userRepository.findByLoginId(command.loginId()))
                    .thenReturn(Optional.empty());

            // stub: 비밀번호 암호화
            when(passwordEncoder.encode(command.password()))
                    .thenReturn("encrypted_password");

            // stub: save 호출 시 저장된 객체 반환
            when(userRepository.save(any(UserModel.class)))
                    .thenAnswer((invocation) -> invocation.getArgument(0));

            // act
            UserModel result = userService.signup(command);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getLoginId()).isEqualTo(command.loginId());

            verify(userRepository, times(1)).save(any(UserModel.class));
        }

        @DisplayName("이미 가입된 로그인 ID로 가입하면, 예외가 발생한다.")
        @Test
        void throwsException_whenLoginIdAlreadyExists() {
            // arrange
            SignupCommand command = new SignupCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // 이미 존재하는 회원 생성
            UserModel existingUser = new UserModel(command.loginId(), "anonymous@123", "기존회원", "1990-01-01", "anonymous@gmail.com");

            // stub: ID가 중복되는 이미 존재하는 UserModel객체 반환
            when(userRepository.findByLoginId(command.loginId()))
                    .thenReturn(Optional.of(existingUser));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.signup(command);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);

            // 행위 검증
            verify(userRepository, never()).save(any());
        }
    }
}
