package com.loopers.user.service;

import com.loopers.user.domain.User;
import com.loopers.user.dto.CreateUserRequest;
import com.loopers.user.dto.GetMyInfoResponse;
import com.loopers.user.exception.InvalidCredentialsException;
import com.loopers.user.exception.SamePasswordException;
import com.loopers.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void 정상_입력시_회원가입_성공() {
        //given
        CreateUserRequest request = new CreateUserRequest(
                "testId", "password123!", "김준영", "1990-04-27", "test@test.com"
        );

        given(passwordEncoder.encode(request.password())).willReturn("encodedPassword");
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        //when
        User user = userService.createUser(request);

        //then
        assertThat(user.getLoginId()).isEqualTo(request.loginId());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void 비밀번호가_암호화되어_저장된다() {
        //given
        String rawPassword = "password123!";
        String encodedPassword = "encoded_password_hash";
        CreateUserRequest request = new CreateUserRequest(
                "testId", rawPassword, "test", "1990-04-27", "test@test.com"
        );

        //password를 암호화한다.
        given(passwordEncoder.encode(rawPassword)).willReturn(encodedPassword);
        //사용자를 저장소에 저장한다.
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        //when
        //회원가입을 진행했을 때
        User user = userService.createUser(request);

        //then
        //비밀번호가 암호화되었는지 확인한다.
        assertThat(user.getPassword()).isEqualTo(encodedPassword);
        //암호화 로직 호출했는지 확인한다.
        verify(passwordEncoder).encode(rawPassword);
    }

    @Test
    void 유효한_로그인ID로_내_정보를_조회한다() {
        // given
        String loginId = "testId";
        User user = User.builder()
                .loginId(loginId)
                .password("encodedPassword")
                .name("홍길동")
                .birthDate("1990-01-01")
                .email("test@test.com")
                .build();

        given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));

        // when
        GetMyInfoResponse response = userService.getMyInfo(loginId);

        // then
        assertThat(response.loginId()).isEqualTo(loginId);
        assertThat(response.name()).isEqualTo("홍길*");
        assertThat(response.birthDate()).isEqualTo("1990-01-01");
        assertThat(response.email()).isEqualTo("test@test.com");
    }

    @Test
    void 존재하지_않는_로그인ID로_조회시_InvalidCredentialsException이_발생한다() {
        // given
        String loginId = "nonExistentId";

        given(userRepository.findByLoginId(loginId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getMyInfo(loginId))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void 올바른_기존_비밀번호로_비밀번호_변경에_성공한다() {
        // given
        String loginId = "testId";
        String currentPassword = "oldPassword123!";
        String newPassword = "newPassword456!";
        String encodedCurrentPassword = "encodedOldPassword";
        String encodedNewPassword = "encodedNewPassword";

        User user = User.builder()
                .loginId(loginId)
                .password(encodedCurrentPassword)
                .name("홍길동")
                .birthDate("1990-01-01")
                .email("test@test.com")
                .build();

        given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(currentPassword, encodedCurrentPassword)).willReturn(true);
        given(passwordEncoder.matches(newPassword, encodedCurrentPassword)).willReturn(false);
        given(passwordEncoder.encode(newPassword)).willReturn(encodedNewPassword);

        // when
        userService.changePassword(loginId, currentPassword, newPassword);

        // then
        assertThat(user.getPassword()).isEqualTo(encodedNewPassword);
    }

    @Test
    void 기존_비밀번호가_일치하지_않으면_InvalidCredentialsException이_발생한다() {
        // given
        String loginId = "testId";
        String wrongPassword = "wrongPassword!";
        String newPassword = "newPassword456!";
        String encodedSavedPassword = "encodedPassword";

        User user = User.builder()
                .loginId(loginId)
                .password(encodedSavedPassword)
                .name("홍길동")
                .birthDate("1990-01-01")
                .email("test@test.com")
                .build();

        given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(wrongPassword, encodedSavedPassword)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.changePassword(loginId, wrongPassword, newPassword))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void 새_비밀번호가_기존_비밀번호와_동일하면_SamePasswordException이_발생한다() {
        // given
        String loginId = "testId";
        String currentPassword = "samePassword123!";
        String newPassword = "samePassword123!";
        String encodedSavedPassword = "encodedPassword";

        User user = User.builder()
                .loginId(loginId)
                .password(encodedSavedPassword)
                .name("홍길동")
                .birthDate("1990-01-01")
                .email("test@test.com")
                .build();

        given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(currentPassword, encodedSavedPassword)).willReturn(true);
        given(passwordEncoder.matches(newPassword, encodedSavedPassword)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.changePassword(loginId, currentPassword, newPassword))
                .isInstanceOf(SamePasswordException.class);
    }

    @Test
    void 새_비밀번호가_규칙에_맞지_않으면_IllegalArgumentException이_발생한다() {
        // given
        String loginId = "testId";
        String currentPassword = "oldPassword123!";
        String invalidNewPassword = "short";  // 8자 미만
        String encodedSavedPassword = "encodedPassword";

        User user = User.builder()
                .loginId(loginId)
                .password(encodedSavedPassword)
                .name("홍길동")
                .birthDate("1990-01-01")
                .email("test@test.com")
                .build();

        given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(currentPassword, encodedSavedPassword)).willReturn(true);
        given(passwordEncoder.matches(invalidNewPassword, encodedSavedPassword)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.changePassword(loginId, currentPassword, invalidNewPassword))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
