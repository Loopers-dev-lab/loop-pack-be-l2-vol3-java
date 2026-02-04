package com.loopers.domain.member;

import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTest {

    @DisplayName("회원 생성 시 각 필드의 검증 로직은 VO에게 위임한다.")
    @Test
    void create_member_success() {
        // given
        MemberId memberId = new MemberId("user1");
        BirthDate birthDate = new BirthDate("1997-01-01");
        Password password = Password.of("Valid123!", birthDate);
        Name name = new Name("앤드류");
        Email email = new Email("test@test.com");

        // when
        Member member = new Member(memberId, password, name, email, birthDate);

        // then
        assertThat(member).isNotNull();
    }
}
