package com.loopers.collector.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 컨슈머 그룹별 토픽 lag 합계를 Micrometer Gauge로 노출 (Prometheus scrape).
 * 로드맵 3단계: lag 알람·대시보드의 입력 지표.
 */
@Component
@ConditionalOnProperty(prefix = "collector.metrics.lag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CollectorConsumerLagMetrics {

    private static final Logger log = LoggerFactory.getLogger(CollectorConsumerLagMetrics.class);

    private final CollectorLagProperties properties;
    private final KafkaProperties kafkaProperties;
    private final MeterRegistry meterRegistry;

    private final Map<String, AtomicLong> lagHolders = new ConcurrentHashMap<>();

    public CollectorConsumerLagMetrics(
            CollectorLagProperties properties,
            KafkaProperties kafkaProperties,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.kafkaProperties = kafkaProperties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${collector.metrics.lag.poll-interval-ms:60000}")
    public void refreshLag() {
        List<CollectorLagProperties.GroupTopics> groups = properties.getGroups();
        if (groups == null || groups.isEmpty()) {
            return;
        }
        Map<String, Object> adminConfig = new HashMap<>();
        adminConfig.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        adminConfig.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000);
        try (AdminClient admin = AdminClient.create(adminConfig)) {
            for (CollectorLagProperties.GroupTopics g : groups) {
                if (g.getGroupId() == null || g.getTopics() == null) {
                    continue;
                }
                for (String topic : g.getTopics()) {
                    if (topic == null || topic.isBlank()) {
                        continue;
                    }
                    try {
                        long lag = computeTopicLag(admin, g.getGroupId(), topic);
                        gaugeHolder(g.getGroupId(), topic).set(lag);
                    } catch (UnknownTopicOrPartitionException e) {
                        log.debug("lag skip unknown topic: {}", topic);
                    } catch (Exception e) {
                        log.warn("lag compute failed group={} topic={}: {}", g.getGroupId(), topic, e.toString());
                    }
                }
            }
        }
    }

    private long computeTopicLag(AdminClient admin, String groupId, String topic) throws Exception {
        DescribeTopicsResult describe = admin.describeTopics(Set.of(topic));
        var td = describe.topicNameValues().get(topic).get(10, TimeUnit.SECONDS);
        Set<TopicPartition> partitions = new HashSet<>();
        td.partitions().forEach(p -> partitions.add(new TopicPartition(topic, p.partition())));

        ListConsumerGroupOffsetsResult committedResult = admin.listConsumerGroupOffsets(groupId);
        Map<TopicPartition, OffsetAndMetadata> committed =
                committedResult.partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);

        Map<TopicPartition, OffsetSpec> latestRequest = new HashMap<>();
        for (TopicPartition tp : partitions) {
            latestRequest.put(tp, OffsetSpec.latest());
        }
        ListOffsetsResult listOffsets = admin.listOffsets(latestRequest);
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latest =
                listOffsets.all().get(10, TimeUnit.SECONDS);

        long sum = 0L;
        for (TopicPartition tp : partitions) {
            ListOffsetsResult.ListOffsetsResultInfo endInfo = latest.get(tp);
            if (endInfo == null) {
                continue;
            }
            long logEnd = endInfo.offset();
            OffsetAndMetadata meta = committed.get(tp);
            long committedNext = meta == null ? 0L : meta.offset();
            sum += ConsumerLagMath.partitionLag(logEnd, committedNext);
        }
        return sum;
    }

    private AtomicLong gaugeHolder(String groupId, String topic) {
        String key = groupId + "|" + topic;
        return lagHolders.computeIfAbsent(key, k -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder("kafka.consumer.topic.lag.sum", value, AtomicLong::get)
                    .tags(Tags.of("group", groupId, "topic", topic))
                    .description("Sum of per-partition consumer lag for the group and topic")
                    .register(meterRegistry);
            return value;
        });
    }
}
