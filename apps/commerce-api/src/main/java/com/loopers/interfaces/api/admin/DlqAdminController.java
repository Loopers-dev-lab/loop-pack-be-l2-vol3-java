package com.loopers.interfaces.api.admin;

import com.loopers.infrastructure.dlq.DlqReprocessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * DLQ 재처리 어드민 API
 *
 * DLQ(pipeline-dlq-v1)에 쌓인 메시지를 수동으로 재처리하기 위한 API.
 *
 * 재처리 프로세스:
 *   1. event_handled에서 해당 eventId 삭제 (멱등성 레코드 초기화)
 *   2. 원본 토픽으로 메시지 재발행
 *   3. 기존 Consumer가 정상 파이프라인으로 재처리
 */
@RestController
@RequestMapping("/admin/dlq")
public class DlqAdminController {

    private final DlqReprocessingService dlqReprocessingService;

    public DlqAdminController(DlqReprocessingService dlqReprocessingService) {
        this.dlqReprocessingService = dlqReprocessingService;
    }

    /**
     * 특정 eventId의 멱등성 레코드 삭제 + 원본 토픽 재발행
     */
    @PostMapping("/reprocess")
    public ResponseEntity<Map<String, Object>> reprocess(@RequestBody DlqReprocessRequest request) {
        dlqReprocessingService.reprocess(request.eventId(), request.originalTopic(),
                request.partitionKey(), request.payload());
        return ResponseEntity.ok(Map.of(
                "status", "REPROCESSED",
                "eventId", request.eventId(),
                "originalTopic", request.originalTopic()
        ));
    }

    /**
     * 특정 eventId의 멱등성 레코드만 삭제 (재발행은 별도 수행)
     */
    @DeleteMapping("/event-handled/{eventId}")
    public ResponseEntity<Map<String, Object>> deleteEventHandled(@PathVariable String eventId) {
        boolean deleted = dlqReprocessingService.deleteEventHandled(eventId);
        return ResponseEntity.ok(Map.of(
                "eventId", eventId,
                "deleted", deleted
        ));
    }

    public record DlqReprocessRequest(
            String eventId,
            String originalTopic,
            String partitionKey,
            String payload
    ) {}
}
