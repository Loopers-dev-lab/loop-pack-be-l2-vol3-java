package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 회원 Facade (퍼사드)
 *
 * Application Layer의 핵심:
 * - 여러 도메인 서비스를 조합하여 하나의 비즈니스 플로우 완성
 * - 트랜잭션 경계 설정
 * - Domain Model을 DTO로 변환하여 반환
 * - 외부(Controller)에 Domain Model을 직접 노출하지 않음
 *
 * Facade 패턴:
 * - 복잡한 서브시스템을 단순한 인터페이스로 제공
 * - 클라이언트와 서브시스템 간의 결합도 감소
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberFacade {

    private final MemberService memberService;

    // ========================================
    // 1. 회원가입
    // ========================================

    /**
     * 회원가입
     *
     * @param loginId 로그인 ID
     * @param loginPw 비밀번호
     * @param name 이름
     * @param birthDate 생년월일
     * @param email 이메일
     * @return 생성된 회원 정보 (DTO)
     */
    @Transactional
    public MemberInfo register(
            String loginId,
            String loginPw,
            String name,
            LocalDate birthDate,
            String email
    ) {
        // 1. 도메인 서비스 호출
        MemberModel member = memberService.register(loginId, loginPw, name, birthDate, email);

        // 2. Domain Model을 DTO로 변환하여 반환
        return MemberInfo.from(member);
    }

    // ========================================
    // 2. 내 정보 조회
    // ========================================

    /**
     * 내 정보 조회 (인증 포함)
     *
     * 비즈니스 플로우:
     * 1. 인증 (로그인 ID + 비밀번호 검증)
     * 2. 회원 정보 조회
     * 3. 이름 마스킹 적용
     * 4. DTO 변환 후 반환
     *
     * @param loginId 로그인 ID
     * @param loginPw 비밀번호
     * @return 마스킹된 회원 정보 (DTO)
     * @throws IllegalArgumentException 인증 실패 시
     */
    public MemberInfo getMyInfo(String loginId, String loginPw) {
        // 1. 인증 (로그인 ID + 비밀번호)
        MemberModel member = memberService.authenticate(loginId, loginPw);

        // 2. Domain Model을 DTO로 변환 (마스킹 자동 적용)
        return MemberInfo.from(member);
    }

    // ========================================
    // 3. 비밀번호 변경
    // ========================================

    /**
     * 비밀번호 변경 (인증 포함)
     *
     * 비즈니스 플로우:
     * 1. 인증 (로그인 ID + 로그인 비밀번호 검증)
     * 2. 비밀번호 변경 (현재 비밀번호 + 새 비밀번호)
     *
     * 주의:
     * - loginPw: 헤더로 전달된 인증용 비밀번호
     * - currentPassword: 본문으로 전달된 현재 비밀번호 (재확인용)
     * - 보안을 위해 두 번 확인
     *
     * @param loginId 로그인 ID
     * @param loginPw 로그인 비밀번호 (인증용)
     * @param currentPassword 현재 비밀번호 (재확인용)
     * @param newPassword 새 비밀번호
     * @throws IllegalArgumentException 인증 실패 또는 비밀번호 변경 실패 시
     */
    @Transactional
    public void changePassword(
            String loginId,
            String loginPw,
            String currentPassword,
            String newPassword
    ) {
        // 1. 인증 (헤더의 로그인 정보)
        memberService.authenticate(loginId, loginPw);

        // 2. 비밀번호 변경 (본문의 현재/새 비밀번호)
        memberService.changePassword(loginId, currentPassword, newPassword);
    }
}
