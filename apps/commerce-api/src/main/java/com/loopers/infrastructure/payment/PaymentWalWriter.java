package com.loopers.infrastructure.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Local WAL (Write-Ahead Log) — PG 응답을 로컬 파일에 먼저 기록.
 *
 * <p>PG 결제 성공 → 내부 DB 저장 실패 시에도 PG 응답을 보존.
 * WalRecoveryScheduler가 주기적으로 WAL 파일을 스캔하여 DB에 반영 재시도.</p>
 *
 * @see <a href="05-payment-resilience.md §8.6">Local WAL</a>
 */
@Slf4j
@Component
public class PaymentWalWriter {

    private final Path walDirectory;
    private final ObjectMapper objectMapper;

    public PaymentWalWriter(
        @Value("${payment.wal.directory:./wal/payments}") String walDirectoryPath,
        ObjectMapper objectMapper
    ) {
        this.walDirectory = Paths.get(walDirectoryPath);
        this.objectMapper = objectMapper;
        ensureDirectoryExists();
    }

    /**
     * PG 응답을 WAL 파일에 기록한다.
     */
    public void write(Long orderId, String transactionKey, String pgStatus) {
        try {
            Map<String, Object> walEntry = Map.of(
                "orderId", orderId,
                "transactionKey", transactionKey,
                "pgStatus", pgStatus,
                "timestamp", System.currentTimeMillis()
            );
            String content = objectMapper.writeValueAsString(walEntry);
            Path walFile = walDirectory.resolve("wal-" + orderId + "-" + transactionKey + ".json");
            Files.writeString(walFile, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug("WAL 기록: orderId={}, transactionKey={}", orderId, transactionKey);
        } catch (IOException e) {
            log.error("WAL 기록 실패: orderId={}, error={}", orderId, e.getMessage());
        }
    }

    /**
     * WAL 파일 삭제 (DB 반영 성공 후).
     */
    public void delete(Path walFile) {
        try {
            Files.deleteIfExists(walFile);
            log.debug("WAL 삭제: {}", walFile.getFileName());
        } catch (IOException e) {
            log.warn("WAL 삭제 실패: {}, error={}", walFile.getFileName(), e.getMessage());
        }
    }

    /**
     * 미처리 WAL 파일 목록 조회.
     */
    public List<Path> listWalFiles() {
        try (Stream<Path> paths = Files.list(walDirectory)) {
            return paths
                .filter(p -> p.toString().endsWith(".json"))
                .toList();
        } catch (IOException e) {
            log.warn("WAL 디렉토리 조회 실패: error={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * WAL 파일 내용 읽기.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> read(Path walFile) {
        try {
            String content = Files.readString(walFile);
            return objectMapper.readValue(content, Map.class);
        } catch (IOException e) {
            log.warn("WAL 파일 읽기 실패: {}, error={}", walFile.getFileName(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(walDirectory);
        } catch (IOException e) {
            log.error("WAL 디렉토리 생성 실패: {}", walDirectory, e);
        }
    }
}
