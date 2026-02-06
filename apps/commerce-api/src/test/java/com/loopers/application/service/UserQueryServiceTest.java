package com.loopers.application.service;

import com.loopers.domain.model.*;
import com.loopers.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserQueryServiceTest {

    private UserRepository userRepository;
    private UserQueryService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new UserQueryService(userRepository);
    }

    @Test
    @DisplayName("내 정보 조회 성공")
    void getUserInfo_success() {
        // given
        UserId userId = UserId.of("test1234");
        LocalDate birthday = LocalDate.of(1990, 5, 15);
        User user = User.reconstitute(
                1L,
                userId,
                UserName.of("홍길동"),
                "encoded_password",
                Birthday.of(birthday),
                Email.of("test@example.com"),
                WrongPasswordCount.init(),
                LocalDateTime.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        var result = service.getUserInfo(userId);

        // then
        assertThat(result.loginId()).isEqualTo("test1234");
        assertThat(result.maskedName()).isEqualTo("홍길*");
        assertThat(result.birthday()).isEqualTo(birthday);
        assertThat(result.email()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("이름 마스킹 - 2자")
    void getUserInfo_maskedName_2chars() {
        // given
        UserId userId = UserId.of("test1234");
        User user = User.reconstitute(
                1L,
                userId,
                UserName.of("홍길"),
                "encoded_password",
                Birthday.of(LocalDate.of(1990, 5, 15)),
                Email.of("test@example.com"),
                WrongPasswordCount.init(),
                LocalDateTime.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        var result = service.getUserInfo(userId);

        // then
        assertThat(result.maskedName()).isEqualTo("홍*");
    }

    @Test
    @DisplayName("존재하지 않는 사용자 조회시 예외")
    void getUserInfo_fail_not_found() {
        // given
        UserId userId = UserId.of("notexist");

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getUserInfo(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("이름은 최소 2자 이상이어야 한다")
    void userName_fail_lessThan2chars() {
        // UserName은 2~20자만 허용하므로 1자는 생성 불가
        assertThatThrownBy(() -> UserName.of("홍"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2~20자");
    }
}
