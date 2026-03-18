package com.loopers.infrastructure.payment.pg;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import feign.Response;
import feign.codec.ErrorDecoder;

public class PgErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        return switch (response.status()) {
            case 400 -> new CoreException(ErrorType.BAD_REQUEST, "PG 요청 오류");
            case 500, 503 -> new PgServerException("PG 서버 오류 (status=" + response.status() + ")");
            default -> new PgServerException("PG 알 수 없는 오류 (status=" + response.status() + ")");
        };
    }
}
