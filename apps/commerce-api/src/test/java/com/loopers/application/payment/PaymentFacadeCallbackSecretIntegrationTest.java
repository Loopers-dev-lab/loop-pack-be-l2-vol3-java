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
 * 콜백 시크릿 검증 (06-payment-change-issues §4.1).
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

    @Test
    @DisplayName("시크릿이 맞지 않으면 UNAUTHORIZED 예외를 던진다.")
    void verifyCallbackSecret_whenMismatch_shouldThrowUnauthorized() {
        CoreException ex = assertThrows(CoreException.class,
                () -> paymentFacade.verifyCallbackSecret("wrong"));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
    }

    @Test
    @DisplayName("시크릿이 일치하면 예외가 없다.")
    void verifyCallbackSecret_whenMatch_shouldNotThrow() {
        assertDoesNotThrow(() -> paymentFacade.verifyCallbackSecret("integration-secret-value"));
    }
}
