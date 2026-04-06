package com.loopers.application.queue;

import com.loopers.application.queue.config.QueueProperties;
import com.loopers.domain.queue.QueueMode;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ModeManager {

    public record ModeState(
            QueueMode mode,
            Instant graceDeadline
    ) {
        static ModeState normal() {
            return new ModeState(QueueMode.NORMAL, Instant.MIN);
        }

        boolean isEvent() { return mode == QueueMode.EVENT; }
        boolean isDrain() { return mode == QueueMode.DRAIN; }
        boolean isInGracePeriod() { return mode == QueueMode.DRAIN && Instant.now().isBefore(graceDeadline); }
    }

    private final QueueProperties queueProperties;
    private volatile ModeState state = ModeState.normal();
    private volatile boolean fallbackMode = false;

    public ModeManager(QueueProperties queueProperties) {
        this.queueProperties = queueProperties;
    }

    public ModeState getState() { return state; }

    // 편의 메서드 — state에 위임

    public boolean isEvent() { return state.isEvent(); }
    public boolean isDrain() { return state.isDrain(); }
    public boolean isInGracePeriod() { return state.isInGracePeriod(); }

    // Redis 장애 fallback

    public boolean isFallbackMode() { return fallbackMode; }
    public void enterFallbackMode() { this.fallbackMode = true; }
    public void exitFallbackMode() { this.fallbackMode = false; }

    // 원자적 전환 — volatile write 1회

    public void switchToEvent() {
        this.state = new ModeState(QueueMode.EVENT, Instant.MIN);
    }

    public void switchToDrain() {
        this.state = new ModeState(
                QueueMode.DRAIN,
                Instant.now().plusSeconds(queueProperties.getGracePeriodSeconds())
        );
    }

    public void switchToNormal() {
        this.state = ModeState.normal();
    }
}
