package com.loopers.application.cdc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CdcConnectConfigSafetyTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("스트리머 CDC topic 패턴은 Debezium DLQ 토픽 cdc-connect-errors 와 매치되지 않는다.")
    void streamerCdcTopicPattern_shouldExcludeErrorsTopic() {
        Pattern pattern = Pattern.compile("^cdc-connect-(?!errors$).+$");
        assertThat(pattern.matcher("cdc-connect-errors").matches()).isFalse();
        assertThat(pattern.matcher("cdc-connect-loopers-product_metrics").matches()).isTrue();
    }

    @Test
    @DisplayName("CDC 커넥터는 Polling 메인 토픽과 충돌하지 않는 전용 토픽으로 라우팅한다.")
    void connector_shouldRouteToDedicatedCdcTopicPrefix() throws Exception {
        JsonNode root = readJson("docker/cdc/connectors/mysql-loopers-connector.json");
        JsonNode config = root.path("config");

        String replacement = config.path("transforms.route.replacement").asText();
        String topicPrefix = config.path("topic.prefix").asText();

        assertThat(topicPrefix).isEqualTo("loopers-cdc");
        assertThat(replacement).startsWith("cdc-connect-");
        assertThat(Set.of("product-events", "order-events", "user-events", "coupon-issue-requests"))
                .doesNotContain(replacement);
        assertThat(config.path("errors.deadletterqueue.topic.name").asText()).isEqualTo("cdc-connect-errors");
    }

    @Test
    @DisplayName("커넥터는 대상 테이블을 제한해 전체 DB 과다 캡처를 방지한다.")
    void connector_shouldRestrictIncludedTables() throws Exception {
        JsonNode root = readJson("docker/cdc/connectors/mysql-loopers-connector.json");
        JsonNode config = root.path("config");

        String includeTables = config.path("table.include.list").asText();
        String includeDb = config.path("database.include.list").asText();

        assertThat(includeDb).isEqualTo("loopers");
        assertThat(includeTables).contains("loopers.product_metrics");
        assertThat(includeTables).contains("loopers.outbox_event");
        assertThat(includeTables).doesNotContain("*");
        assertThat(config.path("snapshot.mode").asText()).isEqualTo("when_needed");
    }

    @Test
    @DisplayName("Connect compose는 ROW 기반 binlog와 CDC 내부 토픽/오류 처리를 설정한다.")
    void compose_shouldEnableRowBinlogAndConnectTopics() throws IOException {
        String compose = readText("docker/cdc/docker-compose.connect.yml");

        assertThat(compose).contains("--binlog-format=ROW");
        assertThat(compose).contains("--binlog-row-image=FULL");
        assertThat(compose).contains("--binlog-expire-logs-seconds=259200");
        assertThat(compose).contains("CONFIG_STORAGE_TOPIC: cdc-connect-configs");
        assertThat(compose).contains("OFFSET_STORAGE_TOPIC: cdc-connect-offsets");
        assertThat(compose).contains("STATUS_STORAGE_TOPIC: cdc-connect-status");
        assertThat(compose).contains("CONNECT_ERRORS_DEADLETTERQUEUE_TOPIC_NAME: cdc-connect-errors");
    }

    @Test
    @DisplayName("커넥터는 키 전략/SMT를 설정해 순서·중복·오버헤드 리스크를 줄인다.")
    void connector_shouldDefineMessageKeyAndTransforms() throws Exception {
        JsonNode root = readJson("docker/cdc/connectors/mysql-loopers-connector.json");
        JsonNode config = root.path("config");

        assertThat(config.path("message.key.columns").asText())
                .contains("loopers.product_metrics:product_id")
                .contains("loopers.outbox_event:id");
        assertThat(config.path("transforms").asText()).isEqualTo("unwrap,route");
        assertThat(config.path("transforms.unwrap.type").asText())
                .isEqualTo("io.debezium.transforms.ExtractNewRecordState");
        assertThat(config.path("transforms.unwrap.add.fields").asText())
                .contains("source.file")
                .contains("source.pos");
    }

    @Test
    @DisplayName("두 커넥터 JSON은 핵심 안전 설정이 동일해야 한다.")
    void connectorFiles_shouldKeepSameSafetyBaseline() throws Exception {
        JsonNode left = readJson("docker/cdc/connectors/mysql-loopers-connector.json").path("config");
        JsonNode right = readJson("docker/cdc/connectors/mysql-loopers-cdc.json").path("config");

        assertThat(right.path("snapshot.mode").asText()).isEqualTo(left.path("snapshot.mode").asText());
        assertThat(right.path("message.key.columns").asText()).isEqualTo(left.path("message.key.columns").asText());
        assertThat(right.path("transforms").asText()).isEqualTo(left.path("transforms").asText());
        assertThat(right.path("transforms.unwrap.add.fields").asText())
                .isEqualTo(left.path("transforms.unwrap.add.fields").asText());
        assertThat(right.path("transforms.route.replacement").asText())
                .isEqualTo(left.path("transforms.route.replacement").asText());
        assertThat(right.path("errors.deadletterqueue.topic.name").asText())
                .isEqualTo(left.path("errors.deadletterqueue.topic.name").asText());
    }

    @Test
    @DisplayName("커넥터 등록 스크립트는 환경변수 기반 URL과 상태 조회 단계를 포함한다.")
    void registerScript_shouldUseConnectUrlAndStatusCheck() throws IOException {
        String script = readText("docker/cdc/register-connector.sh");

        assertThat(script).contains("CONNECT_URL");
        assertThat(script).contains("${CONNECT_URL}/connectors");
        assertThat(script).contains("/status");
        assertThat(script).contains("CONNECTOR_NAME");
        assertThat(script).contains("connector state is not RUNNING");
        assertThat(script).contains("task states are not all RUNNING");
        assertThat(script).contains("export STATUS_JSON");
    }

    private static JsonNode readJson(String relativePath) throws IOException {
        return OBJECT_MAPPER.readTree(readText(relativePath));
    }

    private static String readText(String relativePath) throws IOException {
        Path path = repoRoot().resolve(relativePath);
        return Files.readString(path);
    }

    private static Path repoRoot() {
        Path cur = Path.of("").toAbsolutePath();
        while (cur != null) {
            if (Files.exists(cur.resolve("settings.gradle.kts"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("cannot locate repository root");
    }
}
