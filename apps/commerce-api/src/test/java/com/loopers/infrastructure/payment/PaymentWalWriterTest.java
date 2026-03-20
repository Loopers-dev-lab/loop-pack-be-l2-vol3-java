package com.loopers.infrastructure.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentWalWriterTest {

    @TempDir
    Path tempDir;

    private PaymentWalWriter walWriter;

    @BeforeEach
    void setUp() {
        walWriter = new PaymentWalWriter(tempDir.toString(), new ObjectMapper());
    }

    @DisplayName("U5-7: WAL 기록 → 파일 존재 확인 → 읽기 → 삭제")
    @Test
    void writeAndReadAndDelete() {
        walWriter.write(1L, "TX-001", "SUCCESS");

        // WAL 파일 존재 확인
        List<Path> files = walWriter.listWalFiles();
        assertThat(files).hasSize(1);

        // WAL 파일 읽기
        Map<String, Object> entry = walWriter.read(files.get(0));
        assertThat(entry.get("orderId")).isEqualTo(1);
        assertThat(entry.get("transactionKey")).isEqualTo("TX-001");
        assertThat(entry.get("pgStatus")).isEqualTo("SUCCESS");

        // WAL 파일 삭제
        walWriter.delete(files.get(0));
        assertThat(walWriter.listWalFiles()).isEmpty();
    }

    @DisplayName("빈 디렉토리 → 빈 리스트 반환")
    @Test
    void listEmpty_returnsEmptyList() {
        assertThat(walWriter.listWalFiles()).isEmpty();
    }

    @DisplayName("여러 WAL 파일 기록 → 전부 조회")
    @Test
    void multipleWrites_allListed() {
        walWriter.write(1L, "TX-001", "SUCCESS");
        walWriter.write(2L, "TX-002", "FAILED");

        assertThat(walWriter.listWalFiles()).hasSize(2);
    }
}
