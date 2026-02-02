package com.loopers.interfaces.api.member;

import com.loopers.domain.member.MemberModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MemberV1DtoTest {

    @DisplayName("MyInfoResponse 생성 시 이름 마지막 글자가 *로 마스킹된다")
    @Test
    void myInfoResponse_masksLastCharacterOfName() {
        // arrange
        MemberModel member = new MemberModel(
            "testuser1",
            "encodedPassword",
            "홍길동",
            LocalDate.of(1990, 1, 15),
            "test@example.com"
        );

        // act
        MemberV1Dto.MyInfoResponse response = MemberV1Dto.MyInfoResponse.from(member);

        // assert
        assertThat(response.name()).isEqualTo("홍길*");
    }

    @DisplayName("이름이 1글자면 마스킹하지 않는다")
    @Test
    void myInfoResponse_doesNotMaskSingleCharacterName() {
        // arrange
        MemberModel member = new MemberModel(
            "testuser1",
            "encodedPassword",
            "홍",
            LocalDate.of(1990, 1, 15),
            "test@example.com"
        );

        // act
        MemberV1Dto.MyInfoResponse response = MemberV1Dto.MyInfoResponse.from(member);

        // assert
        assertThat(response.name()).isEqualTo("홍");
    }
}
