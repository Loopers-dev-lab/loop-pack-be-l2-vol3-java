package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PgRequestStatus;
import lombok.Getter;

@Getter
public class PgApiException extends RuntimeException {

    private final PgRequestStatus status;
    private final String pgMessage;

    public PgApiException(PgRequestStatus status, String pgMessage) {
        super(pgMessage);
        this.status = status;
        this.pgMessage = pgMessage;
    }
}
