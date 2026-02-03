package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Component
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[A-Za-z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]{8,16}$");
    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Transactional
    public MemberModel register(String loginId, String password, String name, LocalDate birthDate, String email) {
        validateLoginId(loginId);
        validatePassword(password, birthDate);
        validateName(name);
        validateEmail(email);

        if (memberRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다.");
        }

        String encodedPassword = passwordEncoder.encode(password);
        MemberModel member = new MemberModel(loginId, encodedPassword, name, birthDate, email);
        return memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public MemberModel authenticate(String loginId, String password) {
        MemberModel member = memberRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));

        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }

        return member;
    }

    @Transactional(readOnly = true)
    public MemberModel getMember(String loginId) {
        return memberRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }

    private void validateLoginId(String loginId) {
        if (loginId == null || !LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 허용됩니다.");
        }
    }

    private void validatePassword(String password, LocalDate birthDate) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        }

        String birthDateStr1 = birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String birthDateStr2 = birthDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String birthDateStr3 = birthDate.format(DateTimeFormatter.ofPattern("yyMMdd"));

        if (password.contains(birthDateStr1) || password.contains(birthDateStr2) || password.contains(birthDateStr3)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 한글 또는 영문만 허용됩니다.");
        }
    }

    private void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "올바른 이메일 형식이 아닙니다.");
        }
    }
}
