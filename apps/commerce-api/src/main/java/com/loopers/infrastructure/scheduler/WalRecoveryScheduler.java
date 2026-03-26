package com.loopers.infrastructure.scheduler;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.payment.PaymentWalWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * WAL Recovery 스케줄러 — WAL 파일 스캔 → DB 반영 재시도.
 *
 * <p>PG 응답 수신 후 DB 저장 실패 시 WAL에 남아있는 레코드를 복구.</p>
 *
 * @see <a href="05-payment-resilience.md §8.6">Local WAL Recovery</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalRecoveryScheduler {

    private final PaymentWalWriter walWriter;
    private final PaymentRepository paymentRepository;

    @Scheduled(fixedRate = 10_000)
    public void recoverFromWal() {
        List<Path> walFiles = walWriter.listWalFiles();
        if (walFiles.isEmpty()) return;

        log.info("WAL Recovery 시작: {}건 발견", walFiles.size());

        for (Path walFile : walFiles) {
            try {
                processWalFile(walFile);
            } catch (Exception e) {
                log.error("WAL Recovery 실패: file={}, error={}", walFile.getFileName(), e.getMessage());
            }
        }
    }

    private void processWalFile(Path walFile) {
        Map<String, Object> walEntry = walWriter.read(walFile);
        if (walEntry.isEmpty()) {
            walWriter.delete(walFile);
            return;
        }

        Long orderId = ((Number) walEntry.get("orderId")).longValue();
        String transactionKey = (String) walEntry.get("transactionKey");
        String pgStatus = (String) walEntry.get("pgStatus");

        // Payment 조회
        PaymentModel payment = paymentRepository.findByTransactionKey(transactionKey)
            .or(() -> paymentRepository.findByOrderId(orderId))
            .orElse(null);

        if (payment == null) {
            log.warn("WAL Recovery — Payment 없음: orderId={}, transactionKey={}", orderId, transactionKey);
            walWriter.delete(walFile);
            return;
        }

        // 이미 최종 상태면 WAL 삭제
        if (payment.getStatus().isTerminal()) {
            walWriter.delete(walFile);
            return;
        }

        // PG 상태에 따라 Payment 상태 전이
        List<PaymentStatus> allowedStatuses = List.of(PaymentStatus.PENDING, PaymentStatus.UNKNOWN, PaymentStatus.REQUESTED);
        PaymentStatus targetStatus = "SUCCESS".equals(pgStatus) ? PaymentStatus.PAID : PaymentStatus.FAILED;

        int affected = paymentRepository.updateStatusConditionally(payment.getId(), targetStatus, allowedStatuses);
        if (affected > 0) {
            log.info("WAL Recovery 성공: paymentId={}, newStatus={}", payment.getId(), targetStatus);
        }

        walWriter.delete(walFile);
    }
}
