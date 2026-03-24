package com.loopers.application.payment;

import com.loopers.application.payment.pg.PgClient;
import com.loopers.application.payment.pg.PgPaymentDto;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentReconciliationAppTest {

    PaymentRepository paymentRepository = mock(PaymentRepository.class);
    PgClient pgClient = mock(PgClient.class);
    PaymentApp paymentApp = mock(PaymentApp.class);

    PaymentReconciliationApp reconciliationApp = new PaymentReconciliationApp(paymentRepository, pgClient, paymentApp);

    @DisplayName("Reconciliation 실행 시, ")
    @Nested
    class Reconcile {

        @DisplayName("transactionKey 가 있고 PG에서 SUCCESS 응답이면, applyPgResult 가 SUCCESS 로 반영된다.")
        @Test
        void appliesSuccess_whenTransactionKeyExistsAndPgReturnsSuccess() {
            // arrange
            String orderId = "20260318-ABCD12";
            String transactionKey = "20260318:TR:a1b2c3";

            Payment payment = mock(Payment.class);
            when(payment.orderId()).thenReturn(orderId);
            when(payment.transactionKey()).thenReturn(transactionKey);
            when(paymentRepository.findPendingPaymentsOlderThan(any())).thenReturn(List.of(payment));

            PgPaymentDto.TransactionResponse pgResponse = new PgPaymentDto.TransactionResponse(
                    transactionKey, PgPaymentDto.TransactionStatus.SUCCESS, "정상 승인되었습니다.");
            when(pgClient.getTransaction(transactionKey)).thenReturn(Optional.of(pgResponse));

            // act
            reconciliationApp.reconcile(ZonedDateTime.now().minusMinutes(10));

            // assert
            verify(paymentApp).applyPgResponse(orderId, transactionKey, "SUCCESS", "정상 승인되었습니다.");
        }

        @DisplayName("transactionKey 가 있고 PG에서 404 응답이면, applyPgResponse 가 호출되지 않는다.")
        @Test
        void skips_whenTransactionKeyExistsButPgReturns404() {
            // arrange
            String transactionKey = "20260318:TR:a1b2c3";

            Payment payment = mock(Payment.class);
            when(payment.transactionKey()).thenReturn(transactionKey);
            when(paymentRepository.findPendingPaymentsOlderThan(any())).thenReturn(List.of(payment));
            when(pgClient.getTransaction(transactionKey)).thenReturn(Optional.empty());

            // act
            reconciliationApp.reconcile(ZonedDateTime.now().minusMinutes(10));

            // assert
            verify(paymentApp, never()).applyPgResponse(any(), any(), any(), any());
        }

        @DisplayName("transactionKey 가 없고 PG 목록에 SUCCESS 가 있으면, applyPgResponse 가 SUCCESS 로 반영된다.")
        @Test
        void appliesSuccess_whenNoTransactionKeyAndPgListHasSuccess() {
            // arrange
            String orderId = "20260318-ABCD12";
            String transactionKey = "20260318:TR:a1b2c3";

            Payment payment = mock(Payment.class);
            when(payment.orderId()).thenReturn(orderId);
            when(payment.transactionKey()).thenReturn(null);
            when(paymentRepository.findPendingPaymentsOlderThan(any())).thenReturn(List.of(payment));

            PgPaymentDto.TransactionResponse success = new PgPaymentDto.TransactionResponse(
                    transactionKey, PgPaymentDto.TransactionStatus.SUCCESS, "정상 승인되었습니다.");
            PgPaymentDto.TransactionListResponse listResponse = new PgPaymentDto.TransactionListResponse(
                    orderId, List.of(success));
            when(pgClient.getTransactionsByOrderId(orderId)).thenReturn(Optional.of(listResponse));

            // act
            reconciliationApp.reconcile(ZonedDateTime.now().minusMinutes(10));

            // assert
            verify(paymentApp).applyPgResponse(orderId, transactionKey, "SUCCESS", "정상 승인되었습니다.");
        }

        @DisplayName("transactionKey 가 없고 PG 목록이 전부 FAILED 이면, applyPgResponse 가 FAILED 로 반영된다.")
        @Test
        void appliesFailed_whenNoTransactionKeyAndPgListAllFailed() {
            // arrange
            String orderId = "20260318-ABCD12";
            String transactionKey = "20260318:TR:a1b2c3";

            Payment payment = mock(Payment.class);
            when(payment.orderId()).thenReturn(orderId);
            when(payment.transactionKey()).thenReturn(null);
            when(paymentRepository.findPendingPaymentsOlderThan(any())).thenReturn(List.of(payment));

            PgPaymentDto.TransactionResponse failed = new PgPaymentDto.TransactionResponse(
                    transactionKey, PgPaymentDto.TransactionStatus.FAILED, "한도초과입니다.");
            PgPaymentDto.TransactionListResponse listResponse = new PgPaymentDto.TransactionListResponse(
                    orderId, List.of(failed));
            when(pgClient.getTransactionsByOrderId(orderId)).thenReturn(Optional.of(listResponse));

            // act
            reconciliationApp.reconcile(ZonedDateTime.now().minusMinutes(10));

            // assert
            verify(paymentApp).applyPgResponse(orderId, transactionKey, "FAILED", "한도초과입니다.");
        }

        @DisplayName("transactionKey 가 없고 PG 목록이 PENDING 만 있으면, applyPgResponse 가 호출되지 않는다.")
        @Test
        void skips_whenNoTransactionKeyAndPgListAllPending() {
            // arrange
            String orderId = "20260318-ABCD12";

            Payment payment = mock(Payment.class);
            when(payment.orderId()).thenReturn(orderId);
            when(payment.transactionKey()).thenReturn(null);
            when(paymentRepository.findPendingPaymentsOlderThan(any())).thenReturn(List.of(payment));

            PgPaymentDto.TransactionResponse pending = new PgPaymentDto.TransactionResponse(
                    "20260318:TR:a1b2c3", PgPaymentDto.TransactionStatus.PENDING, null);
            PgPaymentDto.TransactionListResponse listResponse = new PgPaymentDto.TransactionListResponse(
                    orderId, List.of(pending));
            when(pgClient.getTransactionsByOrderId(orderId)).thenReturn(Optional.of(listResponse));

            // act
            reconciliationApp.reconcile(ZonedDateTime.now().minusMinutes(10));

            // assert
            verify(paymentApp, never()).applyPgResponse(any(), any(), any(), any());
        }

        @DisplayName("transactionKey 가 없고 PG 에 내역이 없으면 (404), applyPgResponse 가 FAILED 로 반영된다.")
        @Test
        void appliesFailed_whenNoTransactionKeyAndPgReturns404() {
            // arrange
            String orderId = "20260318-ABCD12";

            Payment payment = mock(Payment.class);
            when(payment.orderId()).thenReturn(orderId);
            when(payment.transactionKey()).thenReturn(null);
            when(paymentRepository.findPendingPaymentsOlderThan(any())).thenReturn(List.of(payment));
            when(pgClient.getTransactionsByOrderId(orderId)).thenReturn(Optional.empty());

            // act
            reconciliationApp.reconcile(ZonedDateTime.now().minusMinutes(10));

            // assert
            verify(paymentApp).applyPgResponse(orderId, null, "FAILED", "PG 내역 없음");
        }

        @DisplayName("특정 Payment 처리 중 예외가 발생해도, 나머지 Payment 는 계속 처리된다.")
        @Test
        void continuesProcessing_whenOnePaymentThrows() {
            // arrange
            String orderId1 = "20260318-ABCD12";
            String orderId2 = "20260318-EFGH34";
            String transactionKey2 = "20260318:TR:d4e5f6";

            Payment payment1 = mock(Payment.class);
            when(payment1.orderId()).thenReturn(orderId1);
            when(payment1.transactionKey()).thenReturn(null);

            Payment payment2 = mock(Payment.class);
            when(payment2.orderId()).thenReturn(orderId2);
            when(payment2.transactionKey()).thenReturn(transactionKey2);

            when(paymentRepository.findPendingPaymentsOlderThan(any())).thenReturn(List.of(payment1, payment2));
            when(pgClient.getTransactionsByOrderId(orderId1)).thenThrow(new RuntimeException("PG 오류"));

            PgPaymentDto.TransactionResponse pgResponse = new PgPaymentDto.TransactionResponse(
                    transactionKey2, PgPaymentDto.TransactionStatus.SUCCESS, "정상 승인되었습니다.");
            when(pgClient.getTransaction(transactionKey2)).thenReturn(Optional.of(pgResponse));

            // act
            reconciliationApp.reconcile(ZonedDateTime.now().minusMinutes(10));

            // assert
            verify(paymentApp).applyPgResponse(orderId2, transactionKey2, "SUCCESS", "정상 승인되었습니다.");
        }
    }
}
