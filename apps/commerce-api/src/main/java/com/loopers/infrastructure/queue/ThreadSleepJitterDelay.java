package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.JitterDelay;
import org.springframework.stereotype.Component;

/**
 * 스레드 대기 및 잡기 딜레이 구현.
 * 주어진 밀리초만큼 스레드를 대기시키며, 잡기 딜레이를 적용한다.
 */
@Component
public class ThreadSleepJitterDelay implements JitterDelay {

    @Override
    public void delay(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

