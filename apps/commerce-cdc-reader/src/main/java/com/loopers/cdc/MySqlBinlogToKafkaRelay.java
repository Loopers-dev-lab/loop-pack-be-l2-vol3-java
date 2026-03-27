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
import java.util.List;
import java.util.Map;

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

    private void publish(TableMapEventData table, String op, Event event, Object rows) {
        String topic = properties.getTopicPrefix() + "-" + table.getDatabase() + "-" + table.getTable();
        String payload = toJson(buildEnvelope(table, op, event, rows));
        kafkaTemplate.send(topic, table.getTable(), payload);
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
