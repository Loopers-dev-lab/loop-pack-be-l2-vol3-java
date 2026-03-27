package com.loopers.infrastructure.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.payment.PgRequestStatus;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PgErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    @Override
    public Exception decode(String methodKey, Response response) {
        PgRequestStatus status = response.status() >= 500
                ? PgRequestStatus.SERVER_ERROR
                : PgRequestStatus.VALIDATION_ERROR;
        String pgMessage = parsePgErrorMessage(response);

        log.warn("PG API 에러 응답: status={}, message={}", response.status(), pgMessage);
        return new PgApiException(status, pgMessage);
    }

    private String parsePgErrorMessage(Response response) {
        try (InputStream body = response.body().asInputStream()) {
            JsonNode node = objectMapper.readTree(body);
            String message = node.path("meta").path("message").asText(null);
            if (message != null && !message.isBlank()) {
                return message;
            }
        } catch (IOException ignored) {
        }
        return "결제 서비스에 일시적인 문제가 발생했습니다.";
    }
}
