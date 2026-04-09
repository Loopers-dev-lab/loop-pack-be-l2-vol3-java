package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.application.collector.CouponIssueConsumeService;
import com.loopers.application.collector.EventDedupService;
import com.loopers.application.collector.ProductMetricsAggregationService;
import com.loopers.application.collector.RealtimeRankingAggregationService;
import com.loopers.domain.collector.CollectorCouponIssueRequestModel;
import com.loopers.domain.collector.CollectorCouponIssueRequestStatus;
import com.loopers.domain.collector.CollectorCouponModel;
import com.loopers.infrastructure.collector.CollectorCouponIssueRequestJpaRepository;
import com.loopers.infrastructure.collector.CollectorCouponJpaRepository;
import com.loopers.infrastructure.collector.CollectorUserCouponJpaRepository;
import com.loopers.kafka.message.KafkaEventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({
    EventDedupService.class,
    ProductMetricsAggregationService.class,
    CouponIssueConsumeService.class,
    CouponIssueKafkaConsumerE2ETest.TestConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CouponIssueKafkaConsumerE2ETest {

    @Autowired
    private CommerceEventKafkaConsumer commerceEventKafkaConsumer;

    @Autowired
    private CollectorCouponJpaRepository couponJpaRepository;

    @Autowired
    private CollectorCouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Autowired
    private CollectorUserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        userCouponJpaRepository.deleteAll();
        couponIssueRequestJpaRepository.deleteAll();
        couponJpaRepository.deleteAll();
    }

    @DisplayName("쿠폰 발급 요청 이벤트를 소비하면 요청 상태가 SUCCEEDED로 변경되고 쿠폰이 발급된다")
    @Test
    void consumesCouponIssueRequestEvent_andUpdatesStatusWithIdempotency() throws Exception {
        // arrange
        CollectorCouponModel coupon = couponJpaRepository.save(new CollectorCouponModel(ZonedDateTime.now().plusDays(1), 10L));
        CollectorCouponIssueRequestModel request = couponIssueRequestJpaRepository.save(
            new CollectorCouponIssueRequestModel(coupon.getId(), 123L)
        );

        KafkaEventEnvelope envelope = new KafkaEventEnvelope(
            UUID.randomUUID().toString(),
            "COUPON_ISSUE_REQUESTED",
            "COUPON",
            String.valueOf(coupon.getId()),
            String.valueOf(coupon.getId()),
            1,
            ZonedDateTime.now(),
            Map.of(
                "requestId", request.getId(),
                "couponId", coupon.getId(),
                "userId", 123L
            )
        );

        String payload = objectMapper.writeValueAsString(envelope);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
            "coupon-issue-requests",
            0,
            0L,
            String.valueOf(coupon.getId()),
            payload
        );
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ReflectionTestUtils.setField(commerceEventKafkaConsumer, "couponConsumerGroup", "commerce-coupon-consumer");

        // act
        commerceEventKafkaConsumer.consumeCouponIssueRequests(List.of(record), acknowledgment);
        commerceEventKafkaConsumer.consumeCouponIssueRequests(List.of(record), acknowledgment);

        // assert
        CollectorCouponIssueRequestModel updatedRequest = couponIssueRequestJpaRepository.findById(request.getId()).orElseThrow();
        CollectorCouponModel updatedCoupon = couponJpaRepository.findById(coupon.getId()).orElseThrow();

        assertThat(updatedRequest.getStatus()).isEqualTo(CollectorCouponIssueRequestStatus.SUCCEEDED);
        assertThat(userCouponJpaRepository.countByCoupon_Id(coupon.getId())).isEqualTo(1);
        assertThat(updatedCoupon.getIssuedCount()).isEqualTo(1L);
        verify(acknowledgment, times(2)).acknowledge();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }

        @Bean
        CommerceEventKafkaConsumer commerceEventKafkaConsumer(
            ObjectMapper objectMapper,
            EventDedupService eventDedupService,
            ProductMetricsAggregationService productMetricsAggregationService,
            RealtimeRankingAggregationService realtimeRankingAggregationService,
            CouponIssueConsumeService couponIssueConsumeService
        ) {
            return new CommerceEventKafkaConsumer(
                objectMapper,
                eventDedupService,
                productMetricsAggregationService,
                realtimeRankingAggregationService,
                couponIssueConsumeService
            );
        }

        @Bean
        RealtimeRankingAggregationService realtimeRankingAggregationService(
        ) {
            return mock(RealtimeRankingAggregationService.class);
        }
    }
}
