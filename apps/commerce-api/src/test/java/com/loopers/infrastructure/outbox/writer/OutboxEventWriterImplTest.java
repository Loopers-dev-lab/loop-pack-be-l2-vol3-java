package com.loopers.infrastructure.outbox.writer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEventService;
import com.loopers.support.error.CoreException;

@ExtendWith(MockitoExtension.class)
class OutboxEventWriterImplTest {

    @InjectMocks
    private OutboxEventWriterImpl outboxEventWriter;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private ObjectMapper objectMapper;

    @DisplayName("Outbox 이벤트를 기록할 때,")
    @Nested
    class Write {

        @DisplayName("직렬화에 성공하면, OutboxEventService에 저장을 위임한다.")
        @Test
        void delegatesToService_whenSerializationSucceeds() throws Exception {
            // arrange
            UUID eventId = UUID.randomUUID();
            Object event = new Object();
            String payload = "{\"data\":\"test\"}";
            given(objectMapper.writeValueAsString(event)).willReturn(payload);

            // act
            outboxEventWriter.write(eventId, 1L, "LIKE", "LIKED", event, "like-liked-v1", "1");

            // assert
            then(outboxEventService).should().save(eventId, 1L, "LIKE", "LIKED", payload, "like-liked-v1", "1");
        }

        @DisplayName("직렬화에 실패하면, CoreException을 던진다.")
        @Test
        void throwsCoreException_whenSerializationFails() throws Exception {
            // arrange
            UUID eventId = UUID.randomUUID();
            Object event = new Object();
            given(objectMapper.writeValueAsString(event)).willThrow(new JsonProcessingException("fail") {
            });

            // act & assert
            assertThatThrownBy(() ->
                    outboxEventWriter.write(eventId, 1L, "LIKE", "LIKED", event, "like-liked-v1", "1")
            ).isInstanceOf(CoreException.class);
        }
    }
}
