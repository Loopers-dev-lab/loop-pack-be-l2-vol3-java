package com.loopers.application.coupon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.event.OutboxEventJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponIssueOutboxIntegrationTest {

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("쿠폰 발급 요청 시 outbox 이벤트가 저장된다")
    @Test
    void createsOutboxEvent_whenIssueRequestIsCreated() throws Exception {
        // arrange
        String loginId = "couponuser";
        String rawPassword = "Test1234!";
        userJpaRepository.save(
            UserModel.createWithEncodedPassword(
                loginId,
                passwordEncoder.encode(rawPassword),
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "couponuser@example.com"
            )
        );

        CouponModel coupon = couponJpaRepository.save(
            new CouponModel("선착순 쿠폰", CouponType.FIXED, 1000L, 0L, ZonedDateTime.now().plusDays(1), 100L)
        );

        // act
        CouponIssueRequestInfo info = couponFacade.requestIssue(loginId, rawPassword, coupon.getId());

        // assert
        var outboxEvents = outboxEventJpaRepository.findTop100ByPublishedAtIsNullOrderByIdAsc();
        assertThat(outboxEvents).isNotEmpty();

        var outbox = outboxEvents.get(0);
        assertThat(outbox.getTopic()).isEqualTo("coupon-issue-requests");
        assertThat(outbox.getEventKey()).isEqualTo(String.valueOf(coupon.getId()));

        JsonNode root = objectMapper.readTree(outbox.getPayload());
        assertThat(root.path("eventType").asText()).isEqualTo("COUPON_ISSUE_REQUESTED");
        assertThat(root.path("payload").path("requestId").asLong()).isEqualTo(info.requestId());
        assertThat(root.path("payload").path("couponId").asLong()).isEqualTo(coupon.getId());
    }
}
