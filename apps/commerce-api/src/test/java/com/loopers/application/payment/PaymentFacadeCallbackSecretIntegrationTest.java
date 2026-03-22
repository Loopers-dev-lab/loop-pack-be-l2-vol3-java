package com.loopers.application.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 역할: {@link PaymentFacade#verifyCallbackSecret}이 설정값과 불일치 시 {@code UNAUTHORIZED}로 막는지
 * Facade 단에서 검증한다 (HTTP는 {@link com.loopers.interfaces.api.payment.PaymentV1PaymentCallbackSecretE2ETest} 참고, 06 §4.1).
 */
@SpringBootTest(properties = "pg.simulator.callback-secret=integration-secret-value")
@Import(MySqlTestContainersConfig.class)
class PaymentFacadeCallbackSecretIntegrationTest {

    @Autowired
    private PaymentFacade paymentFacade;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    /** 위조된 시크릿 문자열 거절. */
    @Test
    @DisplayName("시크릿이 맞지 않으면 UNAUTHORIZED 예외를 던진다.")
    void verifyCallbackSecret_whenMismatch_shouldThrowUnauthorized() {
        CoreException ex = assertThrows(CoreException.class,
                () -> paymentFacade.verifyCallbackSecret("wrong"));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
    }

    /** 정상 PG 콜백이 제출할 값과 일치할 때 통과. */
    @Test
    @DisplayName("시크릿이 일치하면 예외가 없다.")
    void verifyCallbackSecret_whenMatch_shouldNotThrow() {
        assertDoesNotThrow(() -> paymentFacade.verifyCallbackSecret("integration-secret-value"));
    }
}
