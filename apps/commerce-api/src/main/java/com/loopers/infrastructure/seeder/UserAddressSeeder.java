package com.loopers.infrastructure.seeder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * user_addresses 시딩 (~8,000건)
 *
 * 분포:
 * - 유저의 80%(4,000명) 보유
 * - user당: 1개 60% / 2개 30% / 3개 10%
 * - is_default: 유저당 정확히 1개만 true
 * - zip_code: 서울 40% / 경기 30% / 기타 30%
 */
@Component
class UserAddressSeeder {

    private static final Logger log = LoggerFactory.getLogger(UserAddressSeeder.class);
    private static final int BATCH_SIZE = 5_000;
    private static final int USER_COUNT = UserSeeder.USER_COUNT;

    private final JdbcTemplate jdbcTemplate;
    private final Random random = new Random(44);

    private static final String[][] REGIONS = {
        // {zip_prefix, address_line1}
        {"06", "서울시 강남구 테헤란로"},
        {"07", "서울시 서초구 반포대로"},
        {"04", "서울시 용산구 이태원로"},
        {"05", "서울시 마포구 홍익로"},
        {"13", "경기도 성남시 분당구 판교로"},
        {"14", "경기도 수원시 영통구 광교로"},
        {"10", "경기도 고양시 일산동구 중앙로"},
        {"16", "경기도 용인시 수지구 성복로"},
        {"21", "인천시 연수구 송도문화로"},
        {"34", "대전시 유성구 대학로"},
        {"41", "대구시 수성구 범어로"},
        {"48", "부산시 해운대구 센텀중앙로"},
    };

    UserAddressSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void seed() {
        log.info("[UserAddressSeeder] 배송지 데이터 생성 시작");
        long start = System.currentTimeMillis();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        long inserted = 0;

        for (int userId = 1; userId <= USER_COUNT; userId++) {
            if (random.nextDouble() >= 0.80) continue; // 20%는 주소 미등록

            int addressCount = generateAddressCount();

            for (int j = 0; j < addressCount; j++) {
                boolean isDefault = (j == 0); // 첫 번째만 기본 배송지
                int regionIdx = generateRegionIndex();
                String[] region = REGIONS[regionIdx];
                String zipCode = region[0] + String.format("%03d", random.nextInt(1000));

                batch.add(new Object[]{
                    userId,
                    "수령인" + userId + "-" + (j + 1),
                    "010-" + String.format("%04d", random.nextInt(10000)) + "-" + String.format("%04d", random.nextInt(10000)),
                    zipCode,
                    region[1] + " " + (random.nextInt(200) + 1),
                    (random.nextInt(30) + 1) + "층 " + (random.nextInt(10) + 1) + "호",
                    isDefault,
                    Timestamp.from(now.minusDays(random.nextInt(365)).toInstant()),
                    Timestamp.from(now.toInstant()),
                    null // deleted_at
                });

                if (batch.size() >= BATCH_SIZE) {
                    flushBatch(batch);
                    inserted += batch.size();
                    batch.clear();
                }
            }
        }

        if (!batch.isEmpty()) {
            flushBatch(batch);
            inserted += batch.size();
        }

        log.info("[UserAddressSeeder] {}건 생성 완료 ({}ms)", inserted, System.currentTimeMillis() - start);
    }

    /** 1개 60% / 2개 30% / 3개 10% */
    private int generateAddressCount() {
        double r = random.nextDouble();
        if (r < 0.60) return 1;
        if (r < 0.90) return 2;
        return 3;
    }

    /** 서울 40% / 경기 30% / 기타 30% */
    private int generateRegionIndex() {
        double r = random.nextDouble();
        if (r < 0.40) {
            return random.nextInt(4); // 서울 (0~3)
        } else if (r < 0.70) {
            return random.nextInt(4) + 4; // 경기 (4~7)
        } else {
            return random.nextInt(4) + 8; // 기타 (8~11)
        }
    }

    private void flushBatch(List<Object[]> batch) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO user_addresses (user_id, receiver_name, phone, zip_code, " +
            "address_line1, address_line2, is_default, created_at, updated_at, deleted_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            batch
        );
    }
}
