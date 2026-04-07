package com.loopers.collector.cdc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.infrastructure.collector.EventHandledModel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class CdcConnectCollectorService {

    private final CdcEventHandledPersistence eventHandledPersistence;
    private final ObjectMapper objectMapper;
    private final Counter processedCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;

    public CdcConnectCollectorService(
            CdcEventHandledPersistence eventHandledPersistence,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.eventHandledPersistence = eventHandledPersistence;
        this.objectMapper = objectMapper;
        this.processedCounter = meterRegistry.counter("kafka.collector.cdc.events.processed");
        this.duplicateCounter = meterRegistry.counter("kafka.collector.cdc.events.duplicate");
        this.failedCounter = meterRegistry.counter("kafka.collector.cdc.events.failed");
    }

    public void process(ConsumerRecord<Object, Object> record) {
        try {
            JsonNode payload = parsePayload(record.value()); // schema 변화 대응: JsonNode 유연 파싱
            if (payload == null || payload.isMissingNode() || payload.isNull()) {
                throw new IllegalArgumentException("cdc payload is empty");
            }
            // Debezium source(file+pos) 우선, 미존재 시 Kafka offset 기반 fallback.
            String eventId = cdcEventId(record, payload);
            eventHandledPersistence.insert(EventHandledModel.of(
                    eventId,
                    record.topic(),
                    record.partition(),
                    record.offset()
            ));
            processedCounter.increment();
        } catch (DataIntegrityViolationException duplicate) {
            duplicateCounter.increment();
        } catch (IllegalArgumentException e) {
            failedCounter.increment();
            throw e;
        }
    }

    private JsonNode parsePayload(Object rawValue) {
        try {
            byte[] bytes = rawValue instanceof byte[]
                    ? (byte[]) rawValue
                    : String.valueOf(rawValue).getBytes(StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(bytes);
            if (node.isTextual()) {
                node = objectMapper.readTree(node.asText());
            }
            return node;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid cdc event payload", e);
        }
    }

    private String cdcEventId(ConsumerRecord<Object, Object> record, JsonNode payload) {
        String file = readText(payload, "__source_file", "source_file", "source.file", "file");
        String pos = readText(payload, "__source_pos", "source_pos", "source.pos", "pos");
        if (!file.isBlank() && !pos.isBlank()) {
            return "cdc:" + file + ":" + pos;
        }
        return "cdc:" + record.topic() + ":" + record.partition() + ":" + record.offset();
    }

    private String readText(JsonNode payload, String... candidates) {
        for (String key : candidates) {
            JsonNode value = payload.path(key);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return "";
    }
}
