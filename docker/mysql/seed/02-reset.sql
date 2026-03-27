SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE read_model_sync_task;
TRUNCATE TABLE event_handled;
TRUNCATE TABLE like_event_handled;
TRUNCATE TABLE outbox_event;
TRUNCATE TABLE payment_event_log;
TRUNCATE TABLE order_event_log;
TRUNCATE TABLE order_cancel_saga_progress;
TRUNCATE TABLE order_create_saga_progress;
TRUNCATE TABLE product_metrics;
TRUNCATE TABLE likes;
TRUNCATE TABLE payments;
TRUNCATE TABLE order_items;
TRUNCATE TABLE orders;
TRUNCATE TABLE issued_coupons;
TRUNCATE TABLE coupon_issue_requests;
TRUNCATE TABLE coupons;
TRUNCATE TABLE point_balances;
TRUNCATE TABLE members;
TRUNCATE TABLE products;
TRUNCATE TABLE brands;
TRUNCATE TABLE categories;

SET FOREIGN_KEY_CHECKS = 1;
