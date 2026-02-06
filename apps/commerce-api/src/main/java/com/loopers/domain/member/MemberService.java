package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 회원 도메인 서비스
 *
 * 비즈니스 로직:
 * - 회원가입 (중복 확인, 비밀번호 암호화)
 * - 회원 조회
 * - 비밀번호 변경
 * - 인증 (로그인)
 *
 * 예외 처리:
 * - CoreException 사용 (requirements/featured.md ErrorType 준수)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    // ========================================
    // 1. 회원가입
    // ========================================

    /**
     * 회원가입
     *
     * @param loginId 로그인 ID
     * @param loginPw 평문 비밀번호
     * @param name 이름
     * @param birthDate 생년월일
     * @param email 이메일
     * @return 생성된 회원
     * @throws CoreException DUPLICATE_LOGIN_ID - 로그인 ID 중복 시
     */
    @Transactional
    public MemberModel register(
            String loginId,
            String loginPw,
            String name,
            LocalDate birthDate,
            String email
    ) {
        // 1. 로그인 ID 중복 확인
        if (memberRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.DUPLICATE_LOGIN_ID);
        }

        // 2. 비밀번호 유효성 검증 (도메인 규칙)
        MemberModel.validatePassword(loginPw, birthDate);

        // 3. 비밀번호 암호화 (인프라 계층 사용)
        String encodedPassword = passwordEncoder.encode(loginPw);

        // 4. 회원 생성 (이미 암호화된 비밀번호 사용)
        MemberModel member = MemberModel.createWithEncodedPassword(
                loginId,
                encodedPassword,
                name,
                birthDate,
                email
        );

        // 5. 저장
        return memberRepository.save(member);
    }

    // ========================================
    // 2. 회원 조회
    // ========================================

    /**
     * 로그인 ID로 회원 조회
     *
     * @param loginId 로그인 ID
     * @return 회원
     * @throws CoreException NOT_FOUND - 회원을 찾을 수 없을 때
     */
    public MemberModel findByLoginId(String loginId) {
        return memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }

    // ========================================
    // 3. 비밀번호 변경
    // ========================================

    /**
     * 비밀번호 변경
     *
     * @param loginId 로그인 ID
     * @param currentPassword 현재 비밀번호 (평문)
     * @param newPassword 새 비밀번호 (평문)
     * @throws CoreException PASSWORD_MISMATCH - 현재 비밀번호 불일치
     * @throws CoreException SAME_PASSWORD - 새 비밀번호가 기존과 동일
     */
    @Transactional
    public void changePassword(String loginId, String currentPassword, String newPassword) {
        // 1. 회원 조회
        MemberModel member = findByLoginId(loginId);

        // 2. 현재 비밀번호 확인
        if (!passwordEncoder.matches(currentPassword, member.getLoginPw())) {
            throw new CoreException(ErrorType.PASSWORD_MISMATCH);
        }

        // 3. 새 비밀번호가 기존과 동일한지 확인
        if (passwordEncoder.matches(newPassword, member.getLoginPw())) {
            throw new CoreException(ErrorType.SAME_PASSWORD);
        }

        // 4. 새 비밀번호 유효성 검증 (도메인 규칙)
        MemberModel.validatePassword(newPassword, member.getBirthDate());

        // 5. 새 비밀번호 암호화 (인프라 계층 사용)
        String encodedNewPassword = passwordEncoder.encode(newPassword);

        // 6. 비밀번호 변경
        member.updatePassword(encodedNewPassword);
    }

    // ========================================
    // 4. 인증 (로그인)
    // ========================================

    /**
     * 인증 (로그인)
     *
     * @param loginId 로그인 ID
     * @param password 비밀번호 (평문)
     * @return 인증된 회원
     * @throws CoreException UNAUTHORIZED - 인증 실패 시
     */
    public MemberModel authenticate(String loginId, String password) {
        // 1. 회원 조회
        MemberModel member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));

        // 2. 비밀번호 검증
        if (!passwordEncoder.matches(password, member.getLoginPw())) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }

        // 3. 인증 성공
        return member;
    }
}
