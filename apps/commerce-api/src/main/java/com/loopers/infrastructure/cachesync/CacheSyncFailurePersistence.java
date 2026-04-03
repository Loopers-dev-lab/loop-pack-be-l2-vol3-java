package com.loopers.infrastructure.cachesync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.cachesync.CacheSyncAggregateType;
import com.loopers.domain.cachesync.CacheSyncOperationType;
import com.loopers.domain.cachesync.CacheSyncTask;
import com.loopers.domain.cachesync.CacheSyncTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CacheSyncFailurePersistence {

    private final CacheSyncTaskRepository cacheSyncTaskRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBrandUpsertFailure(UUID brandId, Object payload, RuntimeException cause) {
        cacheSyncTaskRepository.save(CacheSyncTask.pending(
                CacheSyncAggregateType.BRAND,
                brandId,
                CacheSyncOperationType.UPSERT,
                writeValue(payload),
                trimError(cause)
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBrandDeleteFailure(UUID brandId, RuntimeException cause) {
        cacheSyncTaskRepository.save(CacheSyncTask.pending(
                CacheSyncAggregateType.BRAND,
                brandId,
                CacheSyncOperationType.DELETE,
                null,
                trimError(cause)
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCategoryUpsertFailure(UUID categoryId, Object payload, RuntimeException cause) {
        cacheSyncTaskRepository.save(CacheSyncTask.pending(
                CacheSyncAggregateType.CATEGORY,
                categoryId,
                CacheSyncOperationType.UPSERT,
                writeValue(payload),
                trimError(cause)
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCategoryDeleteFailure(UUID categoryId, RuntimeException cause) {
        cacheSyncTaskRepository.save(CacheSyncTask.pending(
                CacheSyncAggregateType.CATEGORY,
                categoryId,
                CacheSyncOperationType.DELETE,
                null,
                trimError(cause)
        ));
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("읽기 모델 동기화 실패 payload 직렬화에 실패했습니다.", e);
        }
    }

    private String trimError(RuntimeException cause) {
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
