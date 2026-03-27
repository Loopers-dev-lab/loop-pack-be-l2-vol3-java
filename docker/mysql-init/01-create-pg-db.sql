CREATE DATABASE IF NOT EXISTS paymentgateway CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON paymentgateway.* TO 'application'@'%';
FLUSH PRIVILEGES;
