package com.loopers.application.waitingroom;

import com.loopers.application.waitingroom.dto.WaitingRoomResult;
import com.loopers.domain.waitingroom.WaitingRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WaitingRoomFacade {

    private final WaitingRoomService waitingRoomService;

    public WaitingRoomResult enter(Long userId) {
        return WaitingRoomResult.from(waitingRoomService.enter(userId));
    }

    public WaitingRoomResult getPosition(Long userId) {
        return WaitingRoomResult.from(waitingRoomService.getPosition(userId));
    }

    public void cancel(Long userId) {
        waitingRoomService.cancel(userId);
    }
}
