package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String PASSWORD_PATTERN = "^[A-Za-z0-9!@#$%^&*()_+=-]{8,16}$";

    public MemberModel register(String loginId, String password, String name, LocalDate birthDate, String email) {
        if (memberRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID입니다.");
        }

        if (!password.matches(PASSWORD_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 허용됩니다.");
        }

        if (containsBirthDate(password, birthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }

        String encodedPassword = passwordEncoder.encode(password);
        MemberModel member = new MemberModel(loginId, encodedPassword, name, birthDate, email);
        return memberRepository.save(member);
    }

    private boolean containsBirthDate(String password, LocalDate birthDate) {
        String yyyyMMdd = birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String yyMMdd = birthDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
        return password.contains(yyyyMMdd) || password.contains(yyMMdd);
    }
}
