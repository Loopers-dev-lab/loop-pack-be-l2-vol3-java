package com.loopers.domain.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventService 서비스 테스트")
class OutboxEventServiceTest {

    @Mock
    OutboxEventRepository outboxEventRepository;

    @InjectMocks
    OutboxEventService outboxEventService;

    @Test
    @DisplayName("save 호출 시 OutboxEventModel이 Repository에 저장된다")
    void save_ShouldDelegateToRepository() {
        when(outboxEventRepository.save(any(OutboxEventModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OutboxEventModel result = outboxEventService.save(
                "ORDER", "1001", "ORDER_CREATED",
                "order-events", "1001", "{\"orderId\":1001}"
        );

        assertThat(result.getAggregateType()).isEqualTo("ORDER");
        assertThat(result.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        verify(outboxEventRepository).save(any(OutboxEventModel.class));
    }
}
