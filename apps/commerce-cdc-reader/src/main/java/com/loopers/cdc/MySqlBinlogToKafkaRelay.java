package com.loopers.cdc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.DeleteRowsEventData;
import com.github.shyiko.mysql.binlog.event.Event;
import com.github.shyiko.mysql.binlog.event.EventData;
import com.github.shyiko.mysql.binlog.event.EventHeaderV4;
import com.github.shyiko.mysql.binlog.event.EventType;
import com.github.shyiko.mysql.binlog.event.TableMapEventData;
import com.github.shyiko.mysql.binlog.event.UpdateRowsEventData;
import com.github.shyiko.mysql.binlog.event.WriteRowsEventData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MySQL binlog 이벤트를 Kafka 토픽으로 전달한다.
 * <p>
 * Kafka 전송 실패 시 일시적 브로커 장애에 대비해 {@link CdcReaderProperties#getKafkaSendMaxRetries()} 및
 * {@link CdcReaderProperties#getKafkaSendBackoffInitialMs()} 기준으로 지수 백오프 재시도한다.
 */
@Component
public class MySqlBinlogToKafkaRelay {

    private static final Logger log = LoggerFactory.getLogger(MySqlBinlogToKafkaRelay.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final CdcReaderProperties properties;

    private final Map<Long, TableMapEventData> tableMap = new HashMap<>();

    public MySqlBinlogToKafkaRelay(
            KafkaTemplate<Object, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            CdcReaderProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @jakarta.annotation.PostConstruct
    public void start() {
        BinaryLogClient client = new BinaryLogClient(
                properties.getMysqlHost(),
                properties.getMysqlPort(),
                properties.getUsername(),
                properties.getPassword()
        );
        client.setServerId(properties.getServerId());
        if (properties.getBinlogFilename() != null && !properties.getBinlogFilename().isBlank()) {
            client.setBinlogFilename(properties.getBinlogFilename());
            client.setBinlogPosition(properties.getBinlogPosition());
        }
        client.registerEventListener(this::onEvent);

        Thread t = new Thread(() -> connect(client), "cdc-binlog-reader");
        t.setDaemon(true);
        t.start();
    }

    private void connect(BinaryLogClient client) {
        try {
            client.connect(30_000);
            log.info("cdc reader connected host={} port={}", properties.getMysqlHost(), properties.getMysqlPort());
        } catch (Exception e) {
            throw new IllegalStateException("failed to connect mysql binlog", e);
        }
    }

    private void onEvent(Event event) {
        EventData data = event.getData();
        if (data instanceof TableMapEventData tmd) {
            tableMap.put(tmd.getTableId(), tmd);
            return;
        }

        EventType type = event.getHeader().getEventType();
        if (type == EventType.EXT_WRITE_ROWS || type == EventType.WRITE_ROWS) {
            publishWrite(event, (WriteRowsEventData) data);
        } else if (type == EventType.EXT_UPDATE_ROWS || type == EventType.UPDATE_ROWS) {
            publishUpdate(event, (UpdateRowsEventData) data);
        } else if (type == EventType.EXT_DELETE_ROWS || type == EventType.DELETE_ROWS) {
            publishDelete(event, (DeleteRowsEventData) data);
        }
    }

    private void publishWrite(Event event, WriteRowsEventData data) {
        TableMapEventData table = tableMap.get(data.getTableId());
        if (table == null || !properties.shouldInclude(table.getDatabase(), table.getTable())) {
            return;
        }
        publish(table, "CREATE", event, data.getRows());
    }

    private void publishUpdate(Event event, UpdateRowsEventData data) {
        TableMapEventData table = tableMap.get(data.getTableId());
        if (table == null || !properties.shouldInclude(table.getDatabase(), table.getTable())) {
            return;
        }
        publish(table, "UPDATE", event, data.getRows());
    }

    private void publishDelete(Event event, DeleteRowsEventData data) {
        TableMapEventData table = tableMap.get(data.getTableId());
        if (table == null || !properties.shouldInclude(table.getDatabase(), table.getTable())) {
            return;
        }
        publish(table, "DELETE", event, data.getRows());
    }

    /**
     * 단일 CDC 이벤트를 직렬화해 Kafka로 전송한다.
     * <p>
     * {@link org.springframework.kafka.core.KafkaTemplate#send(Object, Object, Object)} 후
     * {@link java.util.concurrent.Future#get(long, java.util.concurrent.TimeUnit)}로 완료를 기다린다.
     * {@link java.util.concurrent.ExecutionException}, {@link java.util.concurrent.TimeoutException} 발생 시
     * 설정된 횟수만큼 재시도하며, 각 재시도 전 {@link #sleepBackoff(long, int)}로 대기한다.
     * 재시도를 모두 소진하면 {@link IllegalStateException}을 던진다.
     *
     * @param table  테이블 메타
     * @param op     CREATE / UPDATE / DELETE에 대응하는 연산 라벨
     * @param event  binlog 이벤트
     * @param rows   행 데이터
     */
    private void publish(TableMapEventData table, String op, Event event, Object rows) {
        String topic = properties.getTopicPrefix() + "-" + table.getDatabase() + "-" + table.getTable();
        String payload = toJson(buildEnvelope(table, op, event, rows));
        long timeoutMs = properties.getSendTimeout().toMillis();
        int extraRetries = properties.getKafkaSendMaxRetries();
        int maxAttempts = 1 + extraRetries;
        long backoffInitialMs = properties.getKafkaSendBackoffInitialMs();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                kafkaTemplate.send(topic, table.getTable(), payload).get(timeoutMs, TimeUnit.MILLISECONDS);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while sending cdc event topic=" + topic, e);
            } catch (ExecutionException e) {
                if (attempt == maxAttempts - 1) {
                    log.error("Kafka 전송 실패 topic={} db={} table={} op={}", topic, table.getDatabase(), table.getTable(), op, e.getCause());
                    throw new IllegalStateException("kafka send failed topic=" + topic, e.getCause());
                }
                log.warn("Kafka 전송 실패, 재시도 {}/{} topic={} db={} table={} op={}", attempt + 1, extraRetries, topic, table.getDatabase(), table.getTable(), op, e.getCause());
                sleepBackoff(backoffInitialMs, attempt);
            } catch (TimeoutException e) {
                if (attempt == maxAttempts - 1) {
                    log.error("Kafka 전송 타임아웃 topic={} db={} table={} op={} timeoutMs={}", topic, table.getDatabase(), table.getTable(), op, timeoutMs);
                    throw new IllegalStateException("kafka send timeout topic=" + topic, e);
                }
                log.warn("Kafka 전송 타임아웃, 재시도 {}/{} topic={} db={} table={} op={}", attempt + 1, extraRetries, topic, table.getDatabase(), table.getTable(), op);
                sleepBackoff(backoffInitialMs, attempt);
            }
        }
    }

    /**
     * Kafka 재시도 전 대기. {@code min(initialMs * 2^attempt, 10_000)} ms 만큼 슬립한다.
     *
     * @param initialMs 백오프 초기값(ms)
     * @param attempt   0부터 시작하는 시도 인덱스
     */
    private void sleepBackoff(long initialMs, int attempt) {
        long sleepMs = Math.min(initialMs * (1L << attempt), 10_000L);
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted during kafka send backoff", ie);
        }
    }

    private Map<String, Object> buildEnvelope(
            TableMapEventData table,
            String op,
            Event event,
            Object rows
    ) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("op", op);
        envelope.put("database", table.getDatabase());
        envelope.put("table", table.getTable());
        envelope.put("rows", rows);
        EventHeaderV4 header = (EventHeaderV4) event.getHeader();
        envelope.put("timestamp", header.getTimestamp());
        envelope.put("eventType", header.getEventType().name());
        return envelope;
    }

    private String toJson(Map<String, Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to serialize cdc envelope", e);
        }
    }
}
