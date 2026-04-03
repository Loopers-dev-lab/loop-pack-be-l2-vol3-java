-- 선착순 쿠폰 발급 요청: 중복 체크 + 조건부 재고 차감을 원자적으로 처리
-- KEYS[1] = coupon:issue-lock:{templateId}:{userId}  (중복 발급 방지 락)
-- KEYS[2] = coupon:stock:{templateId}                 (재고)
-- ARGV[1] = 락 TTL (초)
--
-- 반환값:
--   -2 = 이미 발급 요청됨 (중복)
--   -1 = 재고 소진 (매진)
--   0 이상 = 성공 (차감 후 잔여 수량)

-- 1. 중복 발급 체크 (SETNX 역할)
if redis.call('EXISTS', KEYS[1]) == 1 then
    return -2
end

-- 2. 재고 확인 — 0 이하면 차감하지 않고 매진 반환
local stock = tonumber(redis.call('GET', KEYS[2]))
if stock == nil or stock <= 0 then
    return -1
end

-- 3. 통과: 락 설정 + 재고 차감
redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
return redis.call('DECR', KEYS[2])
