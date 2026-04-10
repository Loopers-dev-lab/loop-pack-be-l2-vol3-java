package com.loopers.application.user;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.loopers.domain.notification.NotificationSender;
import com.loopers.domain.user.event.UserSignedUpEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignupNotificationHandlerTest {

    @Mock NotificationSender notificationSender;
    SignupNotificationHandler handler;

    UserSignedUpEvent event;

    @BeforeEach
    void setUp() {
        handler = new SignupNotificationHandler(notificationSender);
        event = new UserSignedUpEvent(1L, "홍길동", "test@example.com");
    }

    @DisplayName("회원가입 이벤트를 수신할 때, ")
    @Nested
    class Handle {

        @DisplayName("축하 이메일을 발송한다.")
        @Test
        void handle_sendsWelcomeEmail() {
            // act
            handler.handle(event);

            // assert
            verify(notificationSender).send(
                    "test@example.com",
                    "회원가입을 축하합니다!",
                    "홍길동님, 회원가입을 축하합니다!");
        }

        @DisplayName("예외 발생 시 로그만 남기고 전파하지 않는다.")
        @Test
        void handle_whenExceptionOccurs_doesNotPropagate() {
            // arrange
            doThrow(new RuntimeException("메일 발송 실패"))
                    .when(notificationSender).send(anyString(), anyString(), anyString());

            // act & assert
            assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();
        }
    }
}
