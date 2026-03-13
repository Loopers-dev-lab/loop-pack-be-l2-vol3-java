package com.loopers.infrastructure.category.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.category.CategoryCacheRepository;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.category.Category;
import com.loopers.infrastructure.category.CategoryEntity;
import com.loopers.infrastructure.category.CategoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "loopers.cache.brand-category", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class CategoryCacheRepositoryImpl implements CategoryCacheRepository {

    private static final String CATEGORY_KEY_PREFIX = "category:";
    private static final String CATEGORY_ALL_KEY = "category:all";

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> redisTemplate;
    private final CategoryJpaRepository categoryJpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<Category> findById(UUID id) {
        try {
            String cached = redisTemplate.opsForValue().get(categoryKey(id));
            if (cached != null && !cached.isBlank()) {
                return Optional.of(readCategory(cached));
            }
        } catch (RuntimeException ignored) {
        }

        Optional<Category> category = categoryJpaRepository.findByReferenceIdAndDeletedAtIsNull(id)
                .map(CategoryEntity::toDomain);
        category.ifPresent(this::save);
        return category;
    }

    @Override
    public List<Category> findAll() {
        try {
            String cached = redisTemplate.opsForValue().get(CATEGORY_ALL_KEY);
            if (cached != null && !cached.isBlank()) {
                return readCategories(cached);
            }
        } catch (RuntimeException ignored) {
        }

        List<Category> categories = categoryJpaRepository.findAllByDeletedAtIsNullOrderByIdAsc().stream()
                .map(CategoryEntity::toDomain)
                .toList();
        if (!categories.isEmpty()) {
            saveAll(categories);
        }
        return categories;
    }

    @Override
    public boolean existsById(UUID id) {
        return findById(id).isPresent();
    }

    @Override
    public void save(Category category) {
        try {
            redisTemplate.opsForValue().set(categoryKey(category.id()), writeCategory(CategoryCacheDocument.from(category)));
            redisTemplate.delete(CATEGORY_ALL_KEY);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void delete(UUID id) {
        try {
            redisTemplate.delete(categoryKey(id));
            redisTemplate.delete(CATEGORY_ALL_KEY);
        } catch (RuntimeException ignored) {
        }
    }

    private void saveAll(List<Category> categories) {
        try {
            List<CategoryCacheDocument> documents = categories.stream()
                    .map(CategoryCacheDocument::from)
                    .toList();
            redisTemplate.opsForValue().set(CATEGORY_ALL_KEY, writeCategories(documents));
            for (Category category : categories) {
                redisTemplate.opsForValue().set(categoryKey(category.id()), writeCategory(CategoryCacheDocument.from(category)));
            }
        } catch (RuntimeException ignored) {
        }
    }

    private String categoryKey(UUID id) {
        return CATEGORY_KEY_PREFIX + id;
    }

    private Category readCategory(String raw) {
        try {
            return objectMapper.readValue(raw, CategoryCacheDocument.class).toDomain();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카테고리 Redis 데이터 역직렬화에 실패했습니다.", e);
        }
    }

    private List<Category> readCategories(String raw) {
        try {
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, CategoryCacheDocument.class);
            List<CategoryCacheDocument> documents = objectMapper.readValue(raw, type);
            return documents.stream()
                    .map(CategoryCacheDocument::toDomain)
                    .toList();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카테고리 목록 Redis 데이터 역직렬화에 실패했습니다.", e);
        }
    }

    private String writeCategory(CategoryCacheDocument document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카테고리 Redis 데이터 직렬화에 실패했습니다.", e);
        }
    }

    private String writeCategories(List<CategoryCacheDocument> documents) {
        try {
            return objectMapper.writeValueAsString(documents);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카테고리 목록 Redis 데이터 직렬화에 실패했습니다.", e);
        }
    }
}
