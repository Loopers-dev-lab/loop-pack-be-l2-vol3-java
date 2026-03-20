package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentServiceTest {

    private PaymentService paymentService;
    private FakePaymentRepository fakePaymentRepository;
    private FakePgClient fakePgClient;

    @BeforeEach
    void setUp() {
        fakePaymentRepository = new FakePaymentRepository();
        fakePgClient = new FakePgClient();
        paymentService = new PaymentService(fakePaymentRepository, fakePgClient);
    }

    @DisplayName("결제 요청")
    @Nested
    class RequestPayment {

        @DisplayName("PG 요청 성공 시 PENDING 상태로 저장된다")
        @Test
        void success() {
            fakePgClient.setResponse(new PgClient.PgPaymentResponse("tx-001", "PENDING", null));

            PaymentService.PaymentResult result = paymentService.requestPayment(1L, 1L, 10_000L, "SAMSUNG", "1234-5678-9814-1451");

            assertThat(result.pgTransactionKey()).isEqualTo("tx-001");
            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(fakePaymentRepository.findByPgTransactionKey("tx-001")).isPresent();
        }

        @DisplayName("PG 요청 실패 시 예외가 발생한다")
        @Test
        void pgFails() {
            fakePgClient.setShouldThrow(true);

            assertThatThrownBy(() -> paymentService.requestPayment(1L, 1L, 10_000L, "SAMSUNG", "1234-5678-9814-1451"))
                .isInstanceOf(RuntimeException.class);
        }
    }

    @DisplayName("콜백 처리")
    @Nested
    class HandleCallback {

        @DisplayName("SUCCESS 콜백 수신 시 결제가 SUCCESS로 변경된다")
        @Test
        void successCallback() {
            fakePgClient.setResponse(new PgClient.PgPaymentResponse("tx-001", "PENDING", null));
            paymentService.requestPayment(1L, 1L, 10_000L, "SAMSUNG", "1234-5678-9814-1451");

            paymentService.handleCallback("tx-001", "SUCCESS", null);

            Payment payment = fakePaymentRepository.findByPgTransactionKey("tx-001").orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("FAILED 콜백 수신 시 결제가 FAILED로 변경된다")
        @Test
        void failedCallback() {
            fakePgClient.setResponse(new PgClient.PgPaymentResponse("tx-001", "PENDING", null));
            paymentService.requestPayment(1L, 1L, 10_000L, "SAMSUNG", "1234-5678-9814-1451");

            paymentService.handleCallback("tx-001", "FAILED", "한도 초과");

            Payment payment = fakePaymentRepository.findByPgTransactionKey("tx-001").orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailReason()).isEqualTo("한도 초과");
        }

        @DisplayName("존재하지 않는 transactionKey로 콜백 수신 시 NOT_FOUND 예외가 발생한다")
        @Test
        void notFound() {
            assertThatThrownBy(() -> paymentService.handleCallback("unknown", "SUCCESS", null))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
        }
    }

    static class FakePgClient implements PgClient {
        private PgPaymentResponse response;
        private boolean shouldThrow = false;

        void setResponse(PgPaymentResponse response) {
            this.response = response;
        }

        void setShouldThrow(boolean shouldThrow) {
            this.shouldThrow = shouldThrow;
        }

        @Override
        public PgPaymentResponse requestPayment(PgPaymentRequest request) {
            if (shouldThrow) {
                throw new RuntimeException("PG 서버 오류");
            }
            return response;
        }

        @Override
        public Optional<PgPaymentResponse> getPaymentByTransactionKey(String transactionKey) {
            return Optional.ofNullable(response);
        }

        @Override
        public Optional<PgPaymentResponse> getPaymentByOrderId(String orderId) {
            return Optional.ofNullable(response);
        }
    }

    static class FakePaymentRepository implements PaymentRepository {
        private final Map<Long, Payment> store = new ConcurrentHashMap<>();
        private final List<Payment> list = new ArrayList<>();
        private long nextId = 1;

        @Override
        public Payment save(Payment payment) {
            list.add(payment);
            store.put(nextId++, payment);
            return payment;
        }

        @Override
        public Optional<Payment> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Payment> findByPgTransactionKey(String pgTransactionKey) {
            return list.stream()
                .filter(p -> pgTransactionKey.equals(p.getPgTransactionKey()))
                .findFirst();
        }

        @Override
        public List<Payment> findByStatus(PaymentStatus status) {
            return list.stream()
                .filter(p -> p.getStatus() == status)
                .toList();
        }
    }
}
