package com.loopers.application.metrics;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.metrics.MetricsEventHandler;
import com.loopers.domain.metrics.MetricsPayload;

import lombok.RequiredArgsConstructor;

/**
 * 상품 지표 이벤트 처리를 조율하는 서비스.
 *
 * <p>중복 이벤트를 필터링하고, 등록된 {@link MetricsEventHandler} 중
 * 해당 이벤트 타입을 지원하는 핸들러에 처리를 위임한다.</p>
 */
@Service
@RequiredArgsConstructor
public class ProductMetricsService {

    private final EventHandledRepository eventHandledRepository;
    private final List<MetricsEventHandler> handlers;

    /**
     * 상품 지표 이벤트를 처리한다.
     *
     * <p>이미 처리된 이벤트(eventId 기준)는 무시하고,
     * 이벤트 타입에 매칭되는 핸들러를 찾아 처리를 위임한다.</p>
     *
     * @param meta    이벤트 메타데이터 (eventId, eventType)
     * @param payload 이벤트 페이로드
     */
    @Transactional
    public void handleEvent(MetricsEventMeta meta, MetricsPayload payload) {
        if (!eventHandledRepository.markIfAbsent(meta.eventId())) {
            return;
        }

        MetricsEventHandler handler = handlers.stream()
                .filter(h -> h.supports(meta.eventType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 이벤트 타입입니다: eventType=" + meta.eventType() + ", eventId=" + meta.eventId())
                );
        handler.handle(payload);
    }
}
