package com.loopers.application.member;

import com.loopers.application.member.command.AuthenticateCommand;
import com.loopers.application.member.command.RegisterCommand;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.vo.MemberId;
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
class MemberAuthenticationServiceIntegrationTest {

    @Autowired
    private MemberApplicationService memberApplicationService;

    @Autowired
    private MemberAuthenticationService memberAuthenticationService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("MemberAuthenticationService 통합: 가입 후 인증 성공")
    void authenticate() {
        memberApplicationService.register(new RegisterCommand(
                "memberinteg2",
                "Test1234!@",
                "테스터",
                "memberinteg2@test.com",
                "19900101",
                "010-1111-2223"
        ));

        Member authenticated = memberAuthenticationService.authenticate(
                new AuthenticateCommand(new MemberId("memberinteg2"), "Test1234!@")
        );

        assertThat(authenticated.id().value()).isEqualTo("memberinteg2");
    }
}
