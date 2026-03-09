package com.loopers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BaseStringIdEntity 단위 테스트")
class BaseStringIdEntityTest {

    // 테스트용 구체 클래스 — 실제 서브클래스와 동일 패턴으로 @Id 직접 정의
    @Entity
    @Table(name = "test_entity")
    static class TestEntity extends BaseStringIdEntity {
        @Id
        @UuidGenerator
        @Column(name = "test_id", length = 36)
        private String testId;

        public String getTestId() {
            return testId;
        }
    }

    /**
     * @PrePersist는 JPA가 호출하므로, 테스트에서는 리플렉션으로 직접 호출한다.
     */
    private void invokePrePersist(BaseStringIdEntity entity) throws Exception {
        Method method = BaseStringIdEntity.class.getDeclaredMethod("prePersist");
        method.setAccessible(true);
        method.invoke(entity);
    }

    @Test
    @DisplayName("prePersist 시 createdAt, updatedAt이 설정된다")
    void prePersist_ShouldSetTimestamps() throws Exception {
        // Given
        TestEntity entity = new TestEntity();
        ZonedDateTime before = ZonedDateTime.now();

        // When
        invokePrePersist(entity);

        // Then
        ZonedDateTime after = ZonedDateTime.now();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
        assertThat(entity.getCreatedAt()).isBetween(before, after);
        assertThat(entity.getUpdatedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("softDelete 호출 시 del_yn='Y', deletedAt이 설정된다")
    void softDelete_ShouldSetDelYnYAndDeletedAt() throws Exception {
        // Given
        TestEntity entity = new TestEntity();
        invokePrePersist(entity);

        // When
        entity.softDelete();

        // Then
        assertThat(entity.getDelYn()).isEqualTo("Y");
        assertThat(entity.getDeletedAt()).isNotNull();
        assertThat(entity.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("이미 삭제된 엔티티에 softDelete 호출 시 멱등하다")
    void softDelete_WhenAlreadyDeleted_ShouldBeIdempotent() throws Exception {
        // Given
        TestEntity entity = new TestEntity();
        invokePrePersist(entity);
        entity.softDelete();
        ZonedDateTime firstDeletedAt = entity.getDeletedAt();

        // When
        entity.softDelete();

        // Then
        assertThat(entity.getDelYn()).isEqualTo("Y");
        assertThat(entity.getDeletedAt()).isEqualTo(firstDeletedAt);
    }

    @Test
    @DisplayName("restore 호출 시 del_yn='N', deletedAt이 null이 된다")
    void restore_ShouldSetDelYnNAndClearDeletedAt() throws Exception {
        // Given
        TestEntity entity = new TestEntity();
        invokePrePersist(entity);
        entity.softDelete();

        // When
        entity.restore();

        // Then
        assertThat(entity.getDelYn()).isEqualTo("N");
        assertThat(entity.getDeletedAt()).isNull();
        assertThat(entity.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("삭제되지 않은 엔티티에 restore 호출 시 멱등하다")
    void restore_WhenNotDeleted_ShouldBeIdempotent() throws Exception {
        // Given
        TestEntity entity = new TestEntity();
        invokePrePersist(entity);

        // When
        entity.restore();

        // Then
        assertThat(entity.getDelYn()).isEqualTo("N");
        assertThat(entity.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("isDeleted()가 del_yn 상태를 정확히 반영한다")
    void isDeleted_ShouldReflectDelYnStatus() throws Exception {
        // Given
        TestEntity entity = new TestEntity();
        invokePrePersist(entity);

        // When & Then
        assertThat(entity.isDeleted()).isFalse();

        entity.softDelete();
        assertThat(entity.isDeleted()).isTrue();

        entity.restore();
        assertThat(entity.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("del_yn과 deletedAt은 항상 일관된 상태를 유지한다")
    void delYnAndDeletedAt_ShouldAlwaysBeConsistent() throws Exception {
        // Given
        TestEntity entity = new TestEntity();
        invokePrePersist(entity);

        // 초기 상태: N + null
        assertThat(entity.getDelYn()).isEqualTo("N");
        assertThat(entity.getDeletedAt()).isNull();

        // 삭제: Y + non-null
        entity.softDelete();
        assertThat(entity.getDelYn()).isEqualTo("Y");
        assertThat(entity.getDeletedAt()).isNotNull();

        // 복원: N + null
        entity.restore();
        assertThat(entity.getDelYn()).isEqualTo("N");
        assertThat(entity.getDeletedAt()).isNull();

        // 다시 삭제: Y + non-null
        entity.softDelete();
        assertThat(entity.getDelYn()).isEqualTo("Y");
        assertThat(entity.getDeletedAt()).isNotNull();
    }
}
