package com.loopers.application.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.PasswordEncoder;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberAppService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Member signup(SignupCommand command) {
        MemberId memberId = new MemberId(command.memberId());
        BirthDate birthDate = new BirthDate(command.birthDate());

        if (memberRepository.existsByMemberIdValue(memberId.getValue())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 회원 ID입니다.");
        }

        Password.validate(command.password(), birthDate);
        String encodedPassword = passwordEncoder.encode(command.password());

        Member member = Member.create(
                memberId,
                Password.ofEncoded(encodedPassword),
                new Name(command.name()),
                new Email(command.email()),
                birthDate
        );

        return memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public Member getByMemberId(String memberIdValue) {
        return memberRepository.findByMemberIdValue(memberIdValue)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }

    @Transactional
    public void changePassword(String memberIdValue, String currentPassword, String newPassword) {
        Member member = getByMemberId(memberIdValue);
        member.updatePassword(currentPassword, newPassword, passwordEncoder);
    }

    public record SignupCommand(
            String memberId,
            String password,
            String name,
            String email,
            String birthDate
    ) {}
}
