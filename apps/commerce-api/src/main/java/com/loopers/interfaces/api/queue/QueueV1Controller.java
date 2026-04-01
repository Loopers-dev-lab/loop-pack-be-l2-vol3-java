package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.EnterResult;
import com.loopers.domain.queue.QueuePosition;
import com.loopers.domain.queue.QueueService;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대기열 API. 진입과 순번 조회를 담당한다.
 *
 * <p>기존 {@code OrderV1Controller} 패턴:
 * {@code @RestController} + {@code @RequestMapping} + {@code @AuthUser}로 인증된 사용자 주입.</p>
 *
 * <p>모든 응답은 {@code ApiResponse<T>}로 감싼다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/queue")
public class QueueV1Controller {

    private final QueueService queueService;

    /**
     * 대기열에 진입한다.
     *
     * <p>POST /api/v1/queue/enter</p>
     * <ul>
     *   <li>신규 진입: 201 Created</li>
     *   <li>이미 대기 중 (멱등): 200 OK</li>
     *   <li>대기열 가득: 503 QUEUE_FULL</li>
     * </ul>
     *
     * @param user 인증된 사용자 (@AuthUser)
     * @return 대기열 순번 정보
     */
    @PostMapping("/enter")
    public ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> enter(
            @AuthUser UserModel user) {
        EnterResult result = queueService.enter(user.getUserId());
        QueueV1Dto.EnterResponse response = QueueV1Dto.EnterResponse.from(result);

        HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(response));
    }

    /**
     * 현재 대기 순번을 조회한다.
     *
     * <p>GET /api/v1/queue/position</p>
     * <ul>
     *   <li>WAITING: 대기 중 — 순번, 예상 대기 시간</li>
     *   <li>READY: 입장 토큰 발급됨 — 토큰값 포함</li>
     *   <li>NOT_IN_QUEUE: 대기열 미등록</li>
     * </ul>
     *
     * @param user 인증된 사용자 (@AuthUser)
     * @return 대기 순번 정보
     */
    @GetMapping("/position")
    public ApiResponse<QueueV1Dto.PositionResponse> getPosition(
            @AuthUser UserModel user) {
        QueuePosition position = queueService.getPosition(user.getUserId());
        return ApiResponse.success(QueueV1Dto.PositionResponse.from(position));
    }
}
