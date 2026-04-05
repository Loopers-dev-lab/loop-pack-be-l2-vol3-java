package com.loopers.application.queue;

import com.loopers.domain.queue.QueuePositionInfo;
import com.loopers.domain.queue.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 대기열 관련 유스케이스를 조율하는 Application 레이어 Facade.
// Interfaces(Controller)와 Domain(QueueService) 사이의 진입점 역할을 한다.
// 현재는 QueueService로의 단순 위임이지만, 향후 알림 발송 등
// 여러 도메인 서비스를 조합하는 유스케이스가 추가될 경우 이곳에서 조율한다.
@RequiredArgsConstructor
@Service
public class QueueFacade {

    private final QueueService queueService;

    // 유저를 대기열에 등록하고, 배정된 순번(1-based)을 반환한다.
    public long enter(Long userId) {
        return queueService.enter(userId);
    }

    // 유저의 현재 대기 상태(순번 또는 입장 토큰)를 조회한다.
    public QueuePositionInfo getPosition(Long userId) {
        return queueService.getPosition(userId);
    }
}
