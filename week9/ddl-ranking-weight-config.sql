CREATE TABLE IF NOT EXISTS ranking_weight_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_name  VARCHAR(50) NOT NULL UNIQUE,
    w_view      DOUBLE NOT NULL DEFAULT 0.1,
    w_like      DOUBLE NOT NULL DEFAULT 0.2,
    w_order     DOUBLE NOT NULL DEFAULT 0.7,
    traffic_pct INT NOT NULL DEFAULT 100,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL
);

INSERT INTO ranking_weight_config (group_name, w_view, w_like, w_order, traffic_pct, active, created_at, updated_at)
VALUES ('control', 0.1, 0.2, 0.7, 100, true, NOW(6), NOW(6));
