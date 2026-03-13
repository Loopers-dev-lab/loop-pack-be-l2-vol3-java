package com.loopers.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

@RequiredArgsConstructor
public class TwoLevelCache implements Cache {

    private final Cache l1Cache;
    private final Cache l2Cache;

    @Override
    public String getName() {
        return l2Cache.getName();
    }

    @Override
    public Object getNativeCache() {
        return l2Cache.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper l1Value = l1Cache.get(key);
        if (l1Value != null) {
            return l1Value;
        }

        ValueWrapper l2Value = l2Cache.get(key);
        if (l2Value != null) {
            l1Cache.put(key, l2Value.get());
            return l2Value;
        }

        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        T l1Value = l1Cache.get(key, type);
        if (l1Value != null) {
            return l1Value;
        }

        T l2Value = l2Cache.get(key, type);
        if (l2Value != null) {
            l1Cache.put(key, l2Value);
            return l2Value;
        }

        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        T l1Value = l1Cache.get(key, (Class<T>) Object.class);
        if (l1Value != null) {
            return l1Value;
        }

        T value = l2Cache.get(key, valueLoader);
        if (value != null) {
            l1Cache.put(key, value);
        }
        return value;
    }

    @Override
    public void put(Object key, Object value) {
        l2Cache.put(key, value);
        l1Cache.put(key, value);
    }

    @Override
    public void evict(Object key) {
        l1Cache.evict(key);
        l2Cache.evict(key);
    }

    @Override
    public void clear() {
        l1Cache.clear();
        l2Cache.clear();
    }
}
