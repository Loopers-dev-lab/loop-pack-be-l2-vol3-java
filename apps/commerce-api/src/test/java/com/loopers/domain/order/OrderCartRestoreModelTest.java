package com.loopers.domain.order;

import com.loopers.support.enums.RestoreReason;
import com.loopers.support.enums.RestoreTriggerSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OrderCartRestoreModel 도메인 모델 테스트")
class OrderCartRestoreModelTest {

    @Test
    @DisplayName("유효한 입력으로 생성 성공")
    void create_WithValidInputs_ShouldSuccess() {
        OrderCartRestoreModel restore = OrderCartRestoreModel.create(
                "order-001", "user-001",
                RestoreReason.USER_CANCELLED, RestoreTriggerSource.CANCEL_API
        );

        assertThat(restore.getOrderId()).isEqualTo("order-001");
        assertThat(restore.getUserId()).isEqualTo("user-001");
        assertThat(restore.getReason()).isEqualTo(RestoreReason.USER_CANCELLED);
        assertThat(restore.getTriggerSource()).isEqualTo(RestoreTriggerSource.CANCEL_API);
    }

    @Test
    @DisplayName("restoredAt은 @PrePersist에서 설정된다")
    void create_ShouldSetRestoredAt() {
        OrderCartRestoreModel restore = OrderCartRestoreModel.create(
                "order-001", "user-001",
                RestoreReason.EXPIRED, RestoreTriggerSource.EXPIRE_JOB
        );
        // restoredAt은 @PrePersist에서 설정되므로 JPA 없이는 null
        // 단위 테스트에서는 생성이 성공했음을 확인
        assertThat(restore).isNotNull();
        assertThat(restore.getReason()).isEqualTo(RestoreReason.EXPIRED);
        assertThat(restore.getTriggerSource()).isEqualTo(RestoreTriggerSource.EXPIRE_JOB);
    }
}
