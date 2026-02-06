package com.loopers.infrastructure.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemberRepository 통합 테스트
 *
 * TDD Red Phase: 실패하는 통합 테스트를 먼저 작성
 *
 * @DataJpaTest: JPA 관련 컴포넌트만 로드 (가벼운 테스트)
 * @Import: 테스트에 필요한 추가 빈 등록
 * @ActiveProfiles: 테스트 프로파일 활성화
 */
@DataJpaTest
@Import(MemberRepositoryImpl.class)
@ActiveProfiles("test")
@DisplayName("MemberRepository 통합 테스트")
class MemberRepositoryImplTest {

    @Autowired
    private MemberRepository memberRepository;

    // ========================================
    // 1. 저장 및 조회 테스트
    // ========================================

    @Test
    @DisplayName("회원 저장 및 조회 성공")
    void save_AndFindByLoginId_ShouldSuccess() {
        // Given: 회원 생성
        MemberModel member = MemberModel.createWithEncodedPassword(
                "testuser123",
                "{bcrypt}encoded_password",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "test@example.com"
        );

        // When: 저장
        MemberModel savedMember = memberRepository.save(member);

        // Then: 저장된 회원 확인
        assertThat(savedMember).isNotNull();
        assertThat(savedMember.getId()).isNotNull();  // ID 자동 생성 확인
        assertThat(savedMember.getLoginId()).isEqualTo("testuser123");

        // When: 조회
        Optional<MemberModel> found = memberRepository.findByLoginId("testuser123");

        // Then: 조회 성공
        assertThat(found).isPresent();
        assertThat(found.get().getLoginId()).isEqualTo("testuser123");
        assertThat(found.get().getName()).isEqualTo("홍길동");
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("존재하지 않는 로그인 ID 조회 시 빈 Optional 반환")
    void findByLoginId_WithNonExistingId_ShouldReturnEmpty() {
        // When: 존재하지 않는 ID로 조회
        Optional<MemberModel> result = memberRepository.findByLoginId("nonexistent");

        // Then: 빈 Optional 반환
        assertThat(result).isEmpty();
    }

    // ========================================
    // 2. 중복 확인 테스트
    // ========================================

    @Test
    @DisplayName("존재하는 로그인 ID 중복 체크 - true 반환")
    void existsByLoginId_WithExistingId_ShouldReturnTrue() {
        // Given: 회원 저장
        MemberModel member = MemberModel.createWithEncodedPassword(
                "testuser123",
                "{bcrypt}encoded_password",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "test@example.com"
        );
        memberRepository.save(member);

        // When: 중복 체크
        boolean exists = memberRepository.existsByLoginId("testuser123");

        // Then: true 반환
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 로그인 ID 중복 체크 - false 반환")
    void existsByLoginId_WithNonExistingId_ShouldReturnFalse() {
        // When: 중복 체크
        boolean exists = memberRepository.existsByLoginId("nonexistent");

        // Then: false 반환
        assertThat(exists).isFalse();
    }

    // ========================================
    // 3. 업데이트 테스트
    // ========================================

    @Test
    @DisplayName("회원 정보 수정 성공")
    void update_MemberInfo_ShouldSuccess() {
        // Given: 회원 저장
        MemberModel member = MemberModel.createWithEncodedPassword(
                "testuser123",
                "{bcrypt}encoded_password",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "test@example.com"
        );
        MemberModel savedMember = memberRepository.save(member);

        // When: 비밀번호 변경
        savedMember.updatePassword("{bcrypt}new_encoded_password");
        memberRepository.save(savedMember);  // 변경 사항 저장

        // Then: 변경된 정보 확인
        Optional<MemberModel> updated = memberRepository.findByLoginId("testuser123");
        assertThat(updated).isPresent();
        assertThat(updated.get().getLoginPw()).isEqualTo("{bcrypt}new_encoded_password");
    }

    // ========================================
    // 4. 특수 케이스 테스트
    // ========================================

    @Test
    @DisplayName("대소문자가 다른 로그인 ID는 다른 회원으로 취급")
    void save_WithDifferentCaseLoginId_ShouldBeDifferentMembers() {
        // Given: 대소문자만 다른 로그인 ID로 두 회원 생성
        MemberModel member1 = MemberModel.createWithEncodedPassword(
                "TestUser",
                "{bcrypt}password1",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "test1@example.com"
        );
        MemberModel member2 = MemberModel.createWithEncodedPassword(
                "testuser",
                "{bcrypt}password2",
                "김철수",
                LocalDate.of(1991, 2, 2),
                "test2@example.com"
        );

        // When: 두 회원 저장
        memberRepository.save(member1);
        memberRepository.save(member2);

        // Then: 각각 조회 가능
        Optional<MemberModel> found1 = memberRepository.findByLoginId("TestUser");
        Optional<MemberModel> found2 = memberRepository.findByLoginId("testuser");

        assertThat(found1).isPresent();
        assertThat(found2).isPresent();
        assertThat(found1.get().getName()).isEqualTo("홍길동");
        assertThat(found2.get().getName()).isEqualTo("김철수");
    }

    @Test
    @DisplayName("특수문자가 포함된 이메일 저장 및 조회")
    void save_WithSpecialCharactersInEmail_ShouldSuccess() {
        // Given: 특수문자가 포함된 이메일
        MemberModel member = MemberModel.createWithEncodedPassword(
                "testuser123",
                "{bcrypt}encoded_password",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "test+special@example.co.kr"
        );

        // When: 저장 및 조회
        memberRepository.save(member);
        Optional<MemberModel> found = memberRepository.findByLoginId("testuser123");

        // Then: 정상 저장 및 조회
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test+special@example.co.kr");
    }

    // ========================================
    // 5. 영속성 컨텍스트 테스트
    // ========================================

    @Test
    @DisplayName("동일 트랜잭션 내에서 동일 ID 조회 시 같은 인스턴스 반환")
    void findByLoginId_InSameTransaction_ShouldReturnSameInstance() {
        // Given: 회원 저장
        MemberModel member = MemberModel.createWithEncodedPassword(
                "testuser123",
                "{bcrypt}encoded_password",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "test@example.com"
        );
        memberRepository.save(member);

        // When: 동일 ID로 두 번 조회
        Optional<MemberModel> found1 = memberRepository.findByLoginId("testuser123");
        Optional<MemberModel> found2 = memberRepository.findByLoginId("testuser123");

        // Then: 같은 인스턴스 (JPA 1차 캐시)
        assertThat(found1).isPresent();
        assertThat(found2).isPresent();
        assertThat(found1.get()).isSameAs(found2.get());
    }
}
