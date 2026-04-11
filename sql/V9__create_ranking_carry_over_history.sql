CREATE TABLE ranking_carry_over_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    carry_over_date DATE         NOT NULL,
    carried_rows   INT          NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ranking_carry_over_history_date
        UNIQUE (carry_over_date)
);
