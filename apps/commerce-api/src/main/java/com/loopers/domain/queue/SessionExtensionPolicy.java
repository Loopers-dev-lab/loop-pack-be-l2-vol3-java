package com.loopers.domain.queue;

/**
 * 세션 연장 정책 — 얼마나, 몇 번까지 연장할 수 있는가.
 */
public record SessionExtensionPolicy(
        int hardTtlSeconds,
        int extensionSeconds,
        int maxExtensionsPerMinute
) {}
