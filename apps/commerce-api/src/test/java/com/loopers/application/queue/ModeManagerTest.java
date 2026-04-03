package com.loopers.application.queue;

import com.loopers.interfaces.api.queue.config.QueueProperties;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ModeManagerTest {

    private ModeManager createManager(int gracePeriodSeconds) {
        QueueProperties props = new QueueProperties();
        props.setGracePeriodSeconds(gracePeriodSeconds);
        return new ModeManager(props);
    }

    @Nested
    class 초기_상태 {

        @Test
        void 초기_모드는_NORMAL이다() {
            ModeManager manager = createManager(60);

            assertThat(manager.isEvent()).isFalse();
            assertThat(manager.isDrain()).isFalse();
            assertThat(manager.isInGracePeriod()).isFalse();
        }
    }

    @Nested
    class EVENT_전환 {

        @Test
        void NORMAL에서_EVENT로_전환하면_이벤트_모드가_활성화된다() {
            ModeManager manager = createManager(60);

            manager.switchToEvent();

            assertThat(manager.isEvent()).isTrue();
            assertThat(manager.isDrain()).isFalse();
        }

        @Test
        void EVENT_전환_시_Grace_Period는_활성화되지_않는다() {
            ModeManager manager = createManager(60);

            manager.switchToEvent();

            assertThat(manager.isInGracePeriod()).isFalse();
        }
    }

    @Nested
    class DRAIN_전환 {

        @Test
        void EVENT에서_DRAIN으로_전환하면_드레인_모드가_활성화된다() {
            ModeManager manager = createManager(60);
            manager.switchToEvent();

            manager.switchToDrain();

            assertThat(manager.isDrain()).isTrue();
            assertThat(manager.isEvent()).isFalse();
        }

        @Test
        void DRAIN_전환_시_Grace_Period가_활성화된다() {
            ModeManager manager = createManager(60);
            manager.switchToEvent();

            manager.switchToDrain();

            assertThat(manager.isInGracePeriod()).isTrue();
        }

        @Test
        void Grace_Period는_DRAIN_모드에서만_유효하다() {
            ModeManager manager = createManager(60);
            manager.switchToEvent();
            manager.switchToDrain();
            assertThat(manager.isInGracePeriod()).isTrue();

            manager.switchToNormal();

            assertThat(manager.isInGracePeriod()).isFalse();
        }
    }

    @Nested
    class NORMAL_전환 {

        @Test
        void DRAIN에서_NORMAL로_전환하면_모든_모드가_비활성화된다() {
            ModeManager manager = createManager(60);
            manager.switchToEvent();
            manager.switchToDrain();

            manager.switchToNormal();

            assertThat(manager.isEvent()).isFalse();
            assertThat(manager.isDrain()).isFalse();
            assertThat(manager.isInGracePeriod()).isFalse();
        }

        @Test
        void EVENT에서_NORMAL로_전환할_수_있다() {
            ModeManager manager = createManager(60);
            manager.switchToEvent();

            manager.switchToNormal();

            assertThat(manager.isEvent()).isFalse();
        }
    }

    @Nested
    class Fallback_모드 {

        @Test
        void 초기_상태에서_fallback은_비활성이다() {
            ModeManager manager = createManager(60);

            assertThat(manager.isFallbackMode()).isFalse();
        }

        @Test
        void enterFallbackMode_호출_시_fallback이_활성화된다() {
            ModeManager manager = createManager(60);

            manager.enterFallbackMode();

            assertThat(manager.isFallbackMode()).isTrue();
        }

        @Test
        void exitFallbackMode_호출_시_fallback이_비활성화된다() {
            ModeManager manager = createManager(60);
            manager.enterFallbackMode();

            manager.exitFallbackMode();

            assertThat(manager.isFallbackMode()).isFalse();
        }

        @Test
        void fallbackMode는_기존_모드와_독립적이다() {
            ModeManager manager = createManager(60);
            manager.switchToEvent();
            manager.enterFallbackMode();

            assertThat(manager.isEvent()).isTrue();
            assertThat(manager.isFallbackMode()).isTrue();

            manager.exitFallbackMode();

            assertThat(manager.isEvent()).isTrue();
            assertThat(manager.isFallbackMode()).isFalse();
        }
    }

    @Nested
    class 모드_순환 {

        @Test
        void NORMAL_EVENT_DRAIN_NORMAL_순환이_정상_동작한다() {
            ModeManager manager = createManager(60);
            assertThat(manager.isEvent()).isFalse();

            manager.switchToEvent();
            assertThat(manager.isEvent()).isTrue();

            manager.switchToDrain();
            assertThat(manager.isDrain()).isTrue();

            manager.switchToNormal();
            assertThat(manager.isEvent()).isFalse();
            assertThat(manager.isDrain()).isFalse();
        }
    }
}
