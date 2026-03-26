package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.support.enums.CardType;
import com.loopers.support.enums.PaymentStatus;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({PaymentRepositoryImpl.class, MySqlTestContainersConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("PaymentRepository 통합 테스트")
class PaymentRepositoryImplTest {

    @Autowired
    PaymentRepositoryImpl paymentRepository;

    @Autowired
    TestEntityManager entityManager;

    private PaymentModel createPayment(Long orderId, Long userId) {
        return PaymentModel.create(orderId, userId, CardType.SAMSUNG, "1234-5678-9012-3456", BigDecimal.valueOf(50000));
    }

    @Test
    @DisplayName("저장 시 ID가 자동 생성된다")
    void save_ShouldPersistWithAutoId() {
        PaymentModel payment = createPayment(1L, 100L);

        PaymentModel saved = paymentRepository.save(payment);

        assertThat(saved.getPaymentId()).isNotNull();
        assertThat(saved.getPaymentId()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("transactionKey로 조회 성공")
    void findByTransactionKey_Existing_ShouldReturn() {
        PaymentModel payment = createPayment(1L, 100L);
        payment.assignTransactionKey("20250316:TR:abc123");
        paymentRepository.save(payment);
        entityManager.flush();
        entityManager.clear();

        Optional<PaymentModel> found = paymentRepository.findByTransactionKey("20250316:TR:abc123");

        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("transactionKey로 조회 — 존재하지 않으면 empty")
    void findByTransactionKey_NotExisting_ShouldReturnEmpty() {
        Optional<PaymentModel> found = paymentRepository.findByTransactionKey("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("orderId로 결제 목록 조회")
    void findAllByOrderId_ShouldReturnList() {
        paymentRepository.save(createPayment(1L, 100L));
        paymentRepository.save(createPayment(1L, 100L));
        paymentRepository.save(createPayment(2L, 200L));
        entityManager.flush();
        entityManager.clear();

        List<PaymentModel> payments = paymentRepository.findAllByOrderId(1L);

        assertThat(payments).hasSize(2);
    }

    @Test
    @DisplayName("CAS 상태 전이 — REQUESTED → SUCCESS 성공 시 affected=1")
    void casUpdateStatus_Success_ShouldReturnAffectedRows1() {
        PaymentModel saved = paymentRepository.save(createPayment(1L, 100L));
        entityManager.flush();
        entityManager.clear();

        int affected = paymentRepository.casUpdateStatus(
                saved.getPaymentId(), PaymentStatus.REQUESTED, PaymentStatus.SUCCESS, null);

        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("CAS 상태 전이 — 이미 변경된 상태면 affected=0")
    void casUpdateStatus_AlreadyChanged_ShouldReturnAffectedRows0() {
        PaymentModel saved = paymentRepository.save(createPayment(1L, 100L));
        entityManager.flush();
        entityManager.clear();

        // 첫 번째 CAS: REQUESTED → FAILED
        paymentRepository.casUpdateStatus(
                saved.getPaymentId(), PaymentStatus.REQUESTED, PaymentStatus.FAILED, "테스트 실패");

        // 두 번째 CAS: REQUESTED → SUCCESS (이미 FAILED이므로 실패)
        int affected = paymentRepository.casUpdateStatus(
                saved.getPaymentId(), PaymentStatus.REQUESTED, PaymentStatus.SUCCESS, null);

        assertThat(affected).isEqualTo(0);
    }

    @Test
    @DisplayName("CAS SUCCESS 전이 시 paidAt이 자동 설정된다")
    void casUpdateStatus_ToSuccess_ShouldSetPaidAt() {
        PaymentModel saved = paymentRepository.save(createPayment(1L, 100L));
        entityManager.flush();
        entityManager.clear();

        paymentRepository.casUpdateStatus(
                saved.getPaymentId(), PaymentStatus.REQUESTED, PaymentStatus.SUCCESS, null);

        PaymentModel found = paymentRepository.findById(saved.getPaymentId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(found.getPaidAt()).isNotNull();
        assertThat(found.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("CAS FAILED 전이 시 failureReason이 설정되고 paidAt은 null 유지")
    void casUpdateStatus_ToFailed_ShouldSetFailureReasonAndKeepPaidAtNull() {
        PaymentModel saved = paymentRepository.save(createPayment(1L, 100L));
        entityManager.flush();
        entityManager.clear();

        paymentRepository.casUpdateStatus(
                saved.getPaymentId(), PaymentStatus.REQUESTED, PaymentStatus.FAILED, "잔액 부족");

        PaymentModel found = paymentRepository.findById(saved.getPaymentId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(found.getFailureReason()).isEqualTo("잔액 부족");
        assertThat(found.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("existsByOrderIdAndStatus — 존재하면 true")
    void existsByOrderIdAndStatus_Existing_ShouldReturnTrue() {
        paymentRepository.save(createPayment(1L, 100L));
        entityManager.flush();
        entityManager.clear();

        boolean exists = paymentRepository.existsByOrderIdAndStatus(1L, PaymentStatus.REQUESTED);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByOrderIdAndStatus — 없으면 false")
    void existsByOrderIdAndStatus_NotExisting_ShouldReturnFalse() {
        boolean exists = paymentRepository.existsByOrderIdAndStatus(999L, PaymentStatus.REQUESTED);

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("findAllRequestedBefore — 기준 시각 이전의 REQUESTED 결제 조회")
    void findAllRequestedBefore_ShouldReturnOldRequested() {
        paymentRepository.save(createPayment(1L, 100L));
        entityManager.flush();
        entityManager.clear();

        // 현재 시각 + 1분 이후를 기준으로 조회하면 방금 생성한 건이 조회되어야 함
        List<PaymentModel> payments = paymentRepository.findAllRequestedBefore(
                java.time.LocalDateTime.now().plusMinutes(1));

        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.REQUESTED);
    }
}
