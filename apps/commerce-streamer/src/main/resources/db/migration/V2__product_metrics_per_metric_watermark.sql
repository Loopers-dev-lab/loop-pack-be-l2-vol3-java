-- like/view/sold 각각 워터마크 분리: 단일 last_event_occurred_at 공유 시 늦게 도착한 한 타입이 다른 타입 이벤트를 버리는 문제 방지
ALTER TABLE product_metrics ADD COLUMN last_like_event_occurred_at DATETIME(6) NULL;
ALTER TABLE product_metrics ADD COLUMN last_view_event_occurred_at DATETIME(6) NULL;
ALTER TABLE product_metrics ADD COLUMN last_sold_event_occurred_at DATETIME(6) NULL;
