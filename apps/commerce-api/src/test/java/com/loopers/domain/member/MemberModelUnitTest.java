package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class MemberModelUnitTest {
    @DisplayName("회원 모델을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("ID 가 영문 및 숫자 10자 이내 형식에 맞지 않으면, User 객체 생성에 실패한다.")
        @Test
        void createsMemberModel_whenIdIsInvalid() {
            // arrange
            String memberId = "invalid_id!"; // 특수 문자 포함

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    new MemberModel(memberId, "password123"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);

            // second arrange
            String secondMemberId = "validID12345"; // 11자

            // second act
            CoreException secondResult = assertThrows(CoreException.class, () ->
                    new MemberModel(secondMemberId, "password123"));

            // second assert
            assertThat(secondResult.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일 형식이 xx@yy.zz 형식에 맞지 않으면, User 객체 생성에 실패한다.")
        @Test
        void createsMemberModel_whenEmailIsInvalid() {
            // arrange
            String invalidEmail = "invalid_email";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    new MemberModel("validID1", "password123", invalidEmail));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 형식이 yyyy-MM-dd 형식에 맞지 않으면, User 객체 생성에 실패한다.")
        @Test
        void createdMemberModel_whenBirthDateIsInvalid() {
            // arrange
            String invalidBirthDate = "20230101";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    new MemberModel("validID1", "password123", "valid@email.com", invalidBirthDate));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름은 비어있다면, User 객체 생성에 실패한다.")
        @Test
        void createdMemberModel_whenNameIsEmpty() {
            // arrange
            String name = "";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    new MemberModel("validID1", "password123", "valid@email.com", "2023-01-01", name));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
