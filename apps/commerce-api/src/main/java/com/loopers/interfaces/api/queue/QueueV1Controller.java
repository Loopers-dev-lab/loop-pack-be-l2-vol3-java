package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.application.queue.QueueInfo;
import com.loopers.application.queue.QueuePositionInfo;
import com.loopers.application.queue.QueuePositionStreamService;
import com.loopers.application.user.UserFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueFacade queueFacade;
    private final QueuePositionStreamService queuePositionStreamService;
    private final UserFacade userFacade;

    public QueueV1Controller(
            QueueFacade queueFacade,
            QueuePositionStreamService queuePositionStreamService,
            UserFacade userFacade
    ) {
        this.queueFacade = queueFacade;
        this.queuePositionStreamService = queuePositionStreamService;
        this.userFacade = userFacade;
    }

    @PostMapping("/enter")
    @Override
    public ApiResponse<QueueV1Dto.JoinQueueResponse> joinQueue(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId
    ) {
        Long userId = userFacade.findUserIdByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다."));
        QueueInfo info = queueFacade.joinQueue(userId);
        return ApiResponse.success(QueueV1Dto.JoinQueueResponse.from(info));
    }

    /**
     * 대기 중인 유저의 순번·예상 대기·입장 토큰(해당 시)을 한 번 조회한다(폴링용).
     * 응답 헤더 {@code Retry-After}에는 서버가 권장하는 다음 폴링 간격(초)이 실린다.
     * 대기열에 없으면 {@code 404}, 로그인 헤더 없으면 {@code 401}.
     */
    @GetMapping("/position")
    @Override
    public ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> getQueuePosition(
            @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId
    ) {
        Long userId = userFacade.findUserIdByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다."));
        QueuePositionInfo info = queueFacade.getQueuePosition(userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "대기열에 없습니다."));
        return ResponseEntity.ok()
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(info.retryAfterSeconds()))
                .body(ApiResponse.success(QueueV1Dto.PositionResponse.from(info)));
    }

    /**
     * 순번 스냅샷을 SSE({@code text/event-stream})로 밀어준다. 폴링 대신 실시간 갱신이 필요할 때 사용한다.
     * 연결은 {@link QueuePositionStreamService#subscribe} 정책(타임아웃 등)을 따른다.
     */
    @GetMapping(value = "/position/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Override
    public SseEmitter streamQueuePosition(
            @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId
    ) {
        Long userId = userFacade.findUserIdByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다."));
        return queuePositionStreamService.subscribe(userId);
    }
}

