package com.loopers.infrastructure.pg;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PgFeignErrorDecoder implements ErrorDecoder {
    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();

        if (status >= 400 && status < 500) {
            log.warn("PG 클라이언트 오류 ({}): method={}, reason={}",
                    status, methodKey, response.reason());
            return new CoreException(ErrorType.BAD_REQUEST,
                    String.format("PG 요청 오류 (%d): %s", status, response.reason()));
        }

        if (status >= 500) {
            log.warn("PG 서버 오류 ({}): method={}, reason={}",
                    status, methodKey, response.reason());
            return new RetryableException(
                    status,
                    String.format("PG 서버 오류 (%d): %s", status, response.reason()),
                    response.request().httpMethod(),
                    (Long) null,
                    response.request()
            );
        }

        return defaultDecoder.decode(methodKey, response);
    }
}
