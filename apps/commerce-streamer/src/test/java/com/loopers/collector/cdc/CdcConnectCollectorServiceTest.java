package com.loopers.collector.cdc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.infrastructure.collector.EventHandledJpaRepository;
import com.loopers.infrastructure.collector.EventHandledModel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CdcConnectCollectorServiceTest {

    @Mock
    private EventHandledJpaRepository eventHandledJpaRepository;

    private SimpleMeterRegistry meterRegistry;
    private CdcConnectCollectorService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new CdcConnectCollectorService(
                eventHandledJpaRepository,
                new ObjectMapper().findAndRegisterModules(),
                meterRegistry
        );
    }

    @Test
    @DisplayName("CDC payload는 유연 파싱 후 event_handled에 기록한다.")
    void process_whenValidPayload_shouldRecordHandled() {
        String payload = "{\"id\":1,\"name\":\"ok\",\"newField\":\"ignored\"}";
        ConsumerRecord<Object, Object> record =
                new ConsumerRecord<>("cdc-connect-product_metrics", 0, 11L, "1", payload.getBytes());

        service.process(record);

        verify(eventHandledJpaRepository).saveAndFlush(any());
        assertThat(meterRegistry.find("kafka.collector.cdc.events.processed").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("source file/pos가 있으면 Kafka offset 대신 source 기반 멱등키를 사용한다.")
    void process_whenSourceMetadataExists_shouldUseSourceBasedEventId() {
        String payload = "{\"id\":1,\"__source_file\":\"binlog.000003\",\"__source_pos\":\"128\"}";
        ConsumerRecord<Object, Object> record =
                new ConsumerRecord<>("cdc-connect-product_metrics", 0, 999L, "1", payload.getBytes());

        service.process(record);

        ArgumentCaptor<EventHandledModel> captor = ArgumentCaptor.forClass(EventHandledModel.class);
        verify(eventHandledJpaRepository).saveAndFlush(captor.capture());
        assertThat(readEventId(captor.getValue())).isEqualTo("cdc:binlog.000003:128");
    }

    @Test
    @DisplayName("멱등키 충돌 시 재처리는 중복으로 스킵한다.")
    void process_whenDuplicate_shouldIncreaseDuplicateMetric() {
        ConsumerRecord<Object, Object> record =
                new ConsumerRecord<>("cdc-connect-product_metrics", 0, 11L, "1", "{\"id\":1}".getBytes());
        doThrow(new DataIntegrityViolationException("dup"))
                .when(eventHandledJpaRepository).saveAndFlush(any());

        service.process(record);

        assertThat(meterRegistry.find("kafka.collector.cdc.events.duplicate").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("파싱 불가 payload는 실패 메트릭 증가 후 예외를 던진다.")
    void process_whenInvalidPayload_shouldFail() {
        ConsumerRecord<Object, Object> record =
                new ConsumerRecord<>("cdc-connect-product_metrics", 0, 1L, "1", "{invalid".getBytes());

        assertThrows(IllegalArgumentException.class, () -> service.process(record));
        assertThat(meterRegistry.find("kafka.collector.cdc.events.failed").counter().count()).isEqualTo(1.0);
    }

    private static String readEventId(EventHandledModel model) {
        try {
            Field field = EventHandledModel.class.getDeclaredField("eventId");
            field.setAccessible(true);
            return String.valueOf(field.get(model));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read eventId from EventHandledModel", e);
        }
    }
}
