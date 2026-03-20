package com.loopers.application.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.payment.*;
import com.loopers.fake.FakePaymentRepository;
import com.loopers.infrastructure.payment.PaymentWalWriter;
import com.loopers.infrastructure.scheduler.WalRecoveryScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F7-4: DB 장애 → WAL Recovery 시나리오.
 *
 * <p>PG에서 SUCCESS 응답을 받았으나 DB 저장에 실패한 상황.
 * WAL(Write-Ahead Log)에 PG 응답을 기록해둔 뒤,
 * WAL Recovery 스케줄러가 주기적으로 WAL 파일을 스캔하여 DB에 반영.</p>
 *
 * @see <a href="05-payment-resilience.md §8.6">Local WAL</a>
 */
class DbFailureFaultTest {

    @TempDir
    Path tempDir;

    private PaymentWalWriter walWriter;
    private WalRecoveryScheduler walRecovery;
    private FakePaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository = new FakePaymentRepository();
        walWriter = new PaymentWalWriter(tempDir.toString(), new ObjectMapper());
        walRecovery = new WalRecoveryScheduler(walWriter, paymentRepository);
    }

    @DisplayName("F7-4: PG SUCCESS → DB 실패 → WAL 기록 → WAL Recovery → PAID")
    @Test
    void dbFailure_walRecovery_paid() {
        // Given: Payment(PENDING) 존재 — PG에 요청까지는 성공한 상태
        PaymentModel payment = PaymentModel.create(1L, 5000, "SAMSUNG", "1234");
        payment.markPending("TX-WAL-001", "SIMULATOR");
        payment = paymentRepository.save(payment);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

        // DB 장애 시뮬레이션: PG SUCCESS 응답을 DB에 저장 못 함 → WAL에 기록
        walWriter.write(1L, "TX-WAL-001", "SUCCESS");
        assertThat(walWriter.listWalFiles()).hasSize(1);

        // When: WAL Recovery 스케줄러 실행 (DB 복구 후)
        walRecovery.recoverFromWal();

        // Then: Payment → PAID + WAL 파일 삭제
        PaymentModel recovered = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(walWriter.listWalFiles()).isEmpty();
    }

    @DisplayName("F7-4 변형: PG FAILED → WAL Recovery → Payment FAILED")
    @Test
    void dbFailure_walRecovery_failed() {
        // Given: Payment(PENDING) + PG FAILED WAL
        PaymentModel payment = PaymentModel.create(1L, 5000, "SAMSUNG", "1234");
        payment.markPending("TX-WAL-002", "SIMULATOR");
        payment = paymentRepository.save(payment);

        walWriter.write(1L, "TX-WAL-002", "FAILED");

        // When: WAL Recovery
        walRecovery.recoverFromWal();

        // Then: Payment → FAILED + WAL 삭제
        PaymentModel recovered = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(walWriter.listWalFiles()).isEmpty();
    }

    @DisplayName("이미 최종 상태인 Payment → WAL 삭제만 (중복 처리 안 함)")
    @Test
    void alreadyTerminal_walDeletedOnly() {
        // Given: Payment 이미 PAID 상태
        PaymentModel payment = PaymentModel.create(1L, 5000, "SAMSUNG", "1234");
        payment.markPending("TX-WAL-003", "SIMULATOR");
        payment.markPaid();
        payment = paymentRepository.save(payment);

        walWriter.write(1L, "TX-WAL-003", "SUCCESS");

        // When: WAL Recovery
        walRecovery.recoverFromWal();

        // Then: WAL 삭제 + Payment 상태 변경 없음
        assertThat(walWriter.listWalFiles()).isEmpty();
        PaymentModel unchanged = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @DisplayName("여러 WAL 파일 → 전부 처리")
    @Test
    void multipleWalFiles_allProcessed() {
        // Given: 2개의 WAL 파일 + 대응하는 Payment
        PaymentModel payment1 = PaymentModel.create(1L, 5000, "SAMSUNG", "1234");
        payment1.markPending("TX-WAL-M1", "SIMULATOR");
        payment1 = paymentRepository.save(payment1);

        PaymentModel payment2 = PaymentModel.create(2L, 3000, "HYUNDAI", "5678");
        payment2.markPending("TX-WAL-M2", "SIMULATOR");
        payment2 = paymentRepository.save(payment2);

        walWriter.write(1L, "TX-WAL-M1", "SUCCESS");
        walWriter.write(2L, "TX-WAL-M2", "FAILED");
        assertThat(walWriter.listWalFiles()).hasSize(2);

        // When: WAL Recovery
        walRecovery.recoverFromWal();

        // Then: 모두 처리됨
        assertThat(walWriter.listWalFiles()).isEmpty();
        assertThat(paymentRepository.findById(payment1.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.PAID);
        assertThat(paymentRepository.findById(payment2.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.FAILED);
    }
}
