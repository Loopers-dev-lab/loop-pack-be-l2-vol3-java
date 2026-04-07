package com.loopers.cdc;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * CDC binlog reader 설정.
 * <p>
 * MySQL 계정은 {@code CDC_READER_MYSQL_USERNAME}, {@code CDC_READER_MYSQL_PASSWORD} 환경 변수로 주입할 수 있으며,
 * 미설정 시 기본값(root)을 사용한다.
 */
@ConfigurationProperties(prefix = "cdc.reader")
public class CdcReaderProperties {

    private String mysqlHost = "localhost";
    private int mysqlPort = 3307;
    private String username = "root";
    private String password = "root";
    private long serverId = 223346L;
    private String binlogFilename;
    private long binlogPosition = 4L;

    private String topicPrefix = "cdc-app";
    private List<String> includeDatabases = new ArrayList<>();
    private List<String> includeTables = new ArrayList<>();
    /**
     * Kafka 전송 완료 대기. 미확인 전송 시 binlog만 진행하면 이벤트 유실이 될 수 있어 동기 대기한다.
     */
    private Duration sendTimeout = Duration.ofSeconds(30);
    /** 설정 키: {@code cdc.reader.kafka-send-max-retries}. 상세는 {@link #getKafkaSendMaxRetries()}. */
    private int kafkaSendMaxRetries = 3;
    /** 설정 키: {@code cdc.reader.kafka-send-backoff-initial-ms}. 상세는 {@link #getKafkaSendBackoffInitialMs()}. */
    private long kafkaSendBackoffInitialMs = 100L;

    public String getMysqlHost() {
        return mysqlHost;
    }

    public void setMysqlHost(String mysqlHost) {
        this.mysqlHost = mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public void setMysqlPort(int mysqlPort) {
        this.mysqlPort = mysqlPort;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

    public String getBinlogFilename() {
        return binlogFilename;
    }

    public void setBinlogFilename(String binlogFilename) {
        this.binlogFilename = binlogFilename;
    }

    public long getBinlogPosition() {
        return binlogPosition;
    }

    public void setBinlogPosition(long binlogPosition) {
        this.binlogPosition = binlogPosition;
    }

    public String getTopicPrefix() {
        return topicPrefix;
    }

    public void setTopicPrefix(String topicPrefix) {
        this.topicPrefix = topicPrefix;
    }

    public List<String> getIncludeDatabases() {
        return includeDatabases;
    }

    public void setIncludeDatabases(List<String> includeDatabases) {
        this.includeDatabases = includeDatabases;
    }

    public List<String> getIncludeTables() {
        return includeTables;
    }

    public void setIncludeTables(List<String> includeTables) {
        this.includeTables = includeTables;
    }

    public Duration getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(Duration sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    /**
     * Kafka 전송이 {@link java.util.concurrent.ExecutionException} 또는
     * {@link java.util.concurrent.TimeoutException}으로 실패했을 때 추가로 시도할 횟수(첫 시도 제외).
     * 총 전송 시도 횟수는 {@code 1 + kafkaSendMaxRetries}이다.
     *
     * @return 추가 재시도 횟수
     */
    public int getKafkaSendMaxRetries() {
        return kafkaSendMaxRetries;
    }

    public void setKafkaSendMaxRetries(int kafkaSendMaxRetries) {
        this.kafkaSendMaxRetries = kafkaSendMaxRetries;
    }

    /**
     * 재시도 전 대기 시간의 초기값(ms). {@link com.loopers.cdc.MySqlBinlogToKafkaRelay}에서
     * 매 재시도마다 {@code min(initial * 2^attempt, 10_000)} ms 만큼 대기한다.
     *
     * @return 백오프 초기 대기(ms)
     */
    public long getKafkaSendBackoffInitialMs() {
        return kafkaSendBackoffInitialMs;
    }

    public void setKafkaSendBackoffInitialMs(long kafkaSendBackoffInitialMs) {
        this.kafkaSendBackoffInitialMs = kafkaSendBackoffInitialMs;
    }

    public boolean shouldInclude(String db, String table) {
        if (db == null || table == null) {
            return false;
        }
        if (!includeDatabases.isEmpty() && !includeDatabases.contains(db)) {
            return false;
        }
        if (!includeTables.isEmpty() && !includeTables.contains(db + "." + table)) {
            return false;
        }
        return true;
    }
}
