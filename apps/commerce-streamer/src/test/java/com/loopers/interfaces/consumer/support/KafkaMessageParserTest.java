package com.loopers.interfaces.consumer.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class KafkaMessageParserTest {

    private final KafkaMessageParser parser = new KafkaMessageParser(new ObjectMapper());

    record TestMessage(Long id, String name) {}

    @DisplayName("메시지를 파싱할 때,")
    @Nested
    class Parse {

        @DisplayName("일반 JSON 문자열이면, 정상 역직렬화된다.")
        @Test
        void parsesNormalJson() throws Exception {
            // arrange
            String raw = "{\"id\":1,\"name\":\"test\"}";

            // act
            TestMessage result = parser.parse(raw, TestMessage.class);

            // assert
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(1L),
                    () -> assertThat(result.name()).isEqualTo("test")
            );
        }

        @DisplayName("이중 인코딩된 JSON 문자열이면, 언래핑 후 역직렬화된다.")
        @Test
        void parsesDoubleEncodedJson() throws Exception {
            // arrange — 따옴표로 감싸지고 내부 따옴표가 이스케이프된 형태
            String raw = "\"{\\\"id\\\":1,\\\"name\\\":\\\"test\\\"}\"";

            // act
            TestMessage result = parser.parse(raw, TestMessage.class);

            // assert
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(1L),
                    () -> assertThat(result.name()).isEqualTo("test")
            );
        }

        @DisplayName("유효하지 않은 JSON이면, 예외가 발생한다.")
        @Test
        void throwsException_whenInvalidJson() {
            // act & assert
            assertThatThrownBy(() -> parser.parse("invalid-json", TestMessage.class))
                    .isInstanceOf(Exception.class);
        }
    }
}
