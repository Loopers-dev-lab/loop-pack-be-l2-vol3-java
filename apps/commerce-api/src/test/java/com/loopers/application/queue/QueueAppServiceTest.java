package com.loopers.application.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class QueueAppServiceTest {

    @Mock
    private QueueService queueService;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private QueueAppService queueAppService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(queueAppService, "batchSize", 14);
        ReflectionTestUtils.setField(queueAppService, "fixedRateMs", 100L);
        ReflectionTestUtils.setField(queueAppService, "maxSize", 10000L);
    }

    @Test
    void 대기열에_처음_진입하면_1번_순번을_반환한다() {
        given(queueService.getSize()).willReturn(0L);
        given(queueService.enter(1L)).willReturn(0L);

        long position = queueAppService.enter(1L);

        assertThat(position).isEqualTo(1L);
    }

    @Test
    void 이미_대기_중인_유저가_다시_진입하면_기존_순번을_반환한다() {
        given(queueService.getSize()).willReturn(0L);
        given(queueService.enter(1L)).willReturn(3L);

        long position = queueAppService.enter(1L);

        assertThat(position).isEqualTo(4L);
    }

    @Test
    void 순번_조회_시_예상_대기시간을_함께_반환한다() {
        given(tokenService.validate(1L)).willReturn(false);
        given(queueService.getRank(1L)).willReturn(140L); // 141번째 대기 중

        QueueAppService.QueuePositionResult result = queueAppService.getPosition(1L);

        assertThat(result.position()).isEqualTo(141L);
        assertThat(result.estimatedWaitSeconds()).isEqualTo(1L); // ceil(140 / 140 TPS) = 1초
        assertThat(result.tokenIssued()).isFalse();
    }

    @Test
    void 토큰이_발급되면_nextPollIntervalMs는_0이다() {
        given(tokenService.validate(1L)).willReturn(true);

        QueueAppService.QueuePositionResult result = queueAppService.getPosition(1L);

        assertThat(result.nextPollIntervalMs()).isEqualTo(0L);
    }

    @Test
    void 입장_임박한_유저는_nextPollIntervalMs가_500ms이다() {
        given(tokenService.validate(1L)).willReturn(false);
        given(queueService.getRank(1L)).willReturn(5L); // 6번째 대기 중 (≤ batchSize=14)

        QueueAppService.QueuePositionResult result = queueAppService.getPosition(1L);

        assertThat(result.nextPollIntervalMs()).isEqualTo(500L);
    }

    @Test
    void 대기가_긴_유저는_nextPollIntervalMs가_예상대기시간의_절반이다() {
        given(tokenService.validate(1L)).willReturn(false);
        given(queueService.getRank(1L)).willReturn(999L); // 1000번째 대기 중, estimatedWait = ceil(999/140) = 8초

        QueueAppService.QueuePositionResult result = queueAppService.getPosition(1L);

        assertThat(result.nextPollIntervalMs()).isEqualTo(4000L); // 8 * 500 = 4000
    }

    @Test
    void nextPollIntervalMs는_최대_5000ms를_초과하지_않는다() {
        given(tokenService.validate(1L)).willReturn(false);
        given(queueService.getRank(1L)).willReturn(9999L); // estimatedWait 매우 큼

        QueueAppService.QueuePositionResult result = queueAppService.getPosition(1L);

        assertThat(result.nextPollIntervalMs()).isEqualTo(5000L);
    }
}
