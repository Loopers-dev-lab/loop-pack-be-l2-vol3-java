package com.loopers.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.loopers.application.user.dto.UserCriteria;
import com.loopers.application.user.dto.UserResult;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserFacadeTest {

    @Mock UserService userService;
    UserFacade userFacade;

    UserModel stubUser;
    UserCriteria.Signup criteria;

    @BeforeEach
    void setUp() {
        userFacade = new UserFacade(userService);

        criteria = new UserCriteria.Signup(
                "testuser1", "Test1234!@#", "홍길동", "19900115", "test@example.com");

        stubUser = mock(UserModel.class);
        when(stubUser.getId()).thenReturn(1L);
        when(stubUser.getLoginId()).thenReturn("testuser1");
        when(stubUser.getName()).thenReturn("홍길동");
        when(stubUser.getEmail()).thenReturn("test@example.com");
        when(stubUser.getBirthDate()).thenReturn(LocalDate.of(1990, 1, 15));
        when(stubUser.getBirthDateString()).thenReturn("19900115");
        when(stubUser.getPoint()).thenReturn(0L);

        when(userService.signup("testuser1", "Test1234!@#", "홍길동", "19900115", "test@example.com"))
                .thenReturn(stubUser);
    }

    @DisplayName("회원가입할 때, ")
    @Nested
    class Signup {

        @DisplayName("성공하면 UserResult를 반환한다.")
        @Test
        void signup_returnsUserResult() {
            // act
            UserResult result = userFacade.signup(criteria);

            // assert
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(1L),
                    () -> assertThat(result.loginId()).isEqualTo("testuser1"),
                    () -> assertThat(result.name()).isEqualTo("홍길동"),
                    () -> assertThat(result.email()).isEqualTo("test@example.com"));
        }
    }
}
