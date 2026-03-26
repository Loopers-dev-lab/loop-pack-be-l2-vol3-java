package com.loopers.testcontainers;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

@Configuration
public class KafkaTestContainersConfig {

    private static final KafkaContainer kafkaContainer;

    static {
        kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                .withExposedPorts(9093);
        kafkaContainer.start();

        System.setProperty("spring.kafka.bootstrap-servers", kafkaContainer.getBootstrapServers());

        // 테스트에서 사용할 토픽 미리 생성
        try (AdminClient adminClient = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers()))) {
            adminClient.createTopics(List.of(
                    new NewTopic("catalog-events", 3, (short) 1),
                    new NewTopic("catalog-events.DLQ", 1, (short) 1),
                    new NewTopic("coupon-issue-requests", 3, (short) 1),
                    new NewTopic("coupon-issue-requests.DLQ", 1, (short) 1)
            )).all().get();
        } catch (Exception e) {
            throw new RuntimeException("Kafka 토픽 생성 실패", e);
        }
    }
}
