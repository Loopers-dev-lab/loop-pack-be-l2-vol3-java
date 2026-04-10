package com.loopers.domain.waitingroom;

import com.loopers.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WaitingRoomErrorCode implements ErrorCode {

    NOT_IN_QUEUE(HttpStatus.NOT_FOUND, "QUEUE_001", "대기열에 진입하지 않았습니다."),
    INVALID_TOKEN(HttpStatus.FORBIDDEN, "QUEUE_002", "입장 토큰이 없거나 유효하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
