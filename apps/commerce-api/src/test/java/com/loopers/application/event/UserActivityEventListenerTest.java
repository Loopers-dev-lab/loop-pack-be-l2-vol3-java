package com.loopers.application.event;

import com.loopers.domain.event.UserActivityEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserActivityEventListener 단위 테스트")
class UserActivityEventListenerTest {

    @InjectMocks
    private UserActivityEventListener userActivityEventListener;

    @Test
    @DisplayName("UserActivityEvent 수신 시 예외 없이 로깅한다")
    void shouldLogActivityWithoutException() {
        // given
        UserActivityEvent event = new UserActivityEvent(1L, "PRODUCT_LIKED", 100L, "PRODUCT");

        // when & then
        assertThatCode(() -> userActivityEventListener.handleUserActivity(event))
                .doesNotThrowAnyException();
    }
}
