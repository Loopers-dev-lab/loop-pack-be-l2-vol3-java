package com.loopers.interfaces.api.coderabbit;

public class CodeRabbitV1Dto {
    public record PingResponse(String message, long timestamp) {
        public static PingResponse of(String message, long timestamp) {
            return new PingResponse(message, timestamp);
        }
    }

    public record HelloResponse(String greeting, String target) {
        public static HelloResponse of(String greeting, String target) {
            return new HelloResponse(greeting, target);
        }
    }
}
