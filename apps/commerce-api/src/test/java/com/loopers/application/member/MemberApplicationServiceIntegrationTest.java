package com.loopers.application.member;

import com.loopers.application.member.command.RegisterCommand;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class MemberApplicationServiceIntegrationTest {

    @Autowired
    private MemberApplicationService memberApplicationService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("MemberApplicationService 통합: 가입 후 중복 체크 true")
    void registerAndDuplicateCheck() {
        memberApplicationService.register(new RegisterCommand(
                "memberinteg1",
                "Test1234!@",
                "테스터",
                "memberinteg@test.com",
                "19900101",
                "010-1111-2222"
        ));

        boolean duplicated = memberApplicationService.checkDuplicateLoginId("memberinteg1");

        assertThat(duplicated).isTrue();
    }
}
