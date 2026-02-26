package com.loopers.application.member;

import com.loopers.application.member.command.ChangePasswordCommand;
import com.loopers.application.member.command.RegisterCommand;
import com.loopers.domain.member.PasswordEncoder;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import com.loopers.domain.member.vo.Phone;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class MemberApplicationService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Member register(RegisterCommand command) {
        MemberId memberId = new MemberId(command.memberId());
        Password rawPassword = new Password(command.rawPassword());
        Name name = new Name(command.name());
        Email email = new Email(command.email());
        BirthDate birthDate = BirthDate.of(command.birthDate());
        Phone phone = new Phone(command.phone());

        if (memberRepository.existsByMemberId(memberId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 아이디입니다.");
        }

        Member member = new Member(memberId, rawPassword, name, email, birthDate, phone);
        Password encodedPassword = Password.ofEncoded(passwordEncoder.encode(member.password().value()));
        Member memberWithEncodedPassword = new Member(
                member.id(),
                encodedPassword,
                member.name(),
                member.email(),
                member.birthDate(),
                member.phone()
        );

        try {
            return memberRepository.save(memberWithEncodedPassword);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 아이디입니다.");
        }
    }

    @Transactional(readOnly = true)
    public boolean checkDuplicateLoginId(String loginId) {
        MemberId memberId = new MemberId(loginId);
        return memberRepository.existsByMemberId(memberId);
    }

    @Transactional
    public void changePassword(ChangePasswordCommand command) {
        Member member = memberRepository.findByMemberId(command.memberId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (passwordEncoder.matches(command.newRawPassword(), member.password().value())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 기존 비밀번호와 다르게 설정해야 합니다.");
        }

        Password newRawPassword = new Password(command.newRawPassword());
        new Member(member.id(), newRawPassword, member.name(), member.email(), member.birthDate(), member.phone());
        Password encodedPassword = Password.ofEncoded(passwordEncoder.encode(newRawPassword.value()));
        Member updatedMember = new Member(
                member.id(),
                encodedPassword,
                member.name(),
                member.email(),
                member.birthDate(),
                member.phone()
        );
        memberRepository.save(updatedMember);
    }
}
