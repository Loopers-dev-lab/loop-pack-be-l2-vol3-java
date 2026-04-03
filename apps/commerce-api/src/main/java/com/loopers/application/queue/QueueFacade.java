package com.loopers.application.queue;

import com.loopers.application.queue.dto.QueuePositionResDto;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.queue.service.EntryTokenService;
import com.loopers.domain.queue.service.QueueFeatureFlag;
import com.loopers.domain.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@RequiredArgsConstructor
@Component
public class QueueFacade {

    private final MemberService memberService;
    private final QueueService queueService;
    private final EntryTokenService entryTokenService;
    private final QueueFeatureFlag queueFeatureFlag;

    public QueuePositionResDto enterQueue(String loginId, String password) {
        if (!queueFeatureFlag.isEnabled()) {
            return QueuePositionResDto.bypass();
        }
        Member member = memberService.findMember(loginId, password);
        return QueuePositionResDto.from(queueService.enterWithLua(member.getId()));
    }

    public QueuePositionResDto getPosition(String loginId, String password) {
        if (!queueFeatureFlag.isEnabled()) {
            return QueuePositionResDto.bypass();
        }
        Member member = memberService.findMember(loginId, password);

        String token = entryTokenService.getToken(member.getId());
        if (token != null) {
            long delayMs = entryTokenService.getRemainingDelayMs(member.getId());
            return QueuePositionResDto.withToken(token, delayMs);
        }

        return QueuePositionResDto.from(queueService.getPosition(member.getId()));
    }
}
