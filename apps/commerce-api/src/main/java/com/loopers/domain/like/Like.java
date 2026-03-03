package com.loopers.domain.like;
import java.util.UUID;

public record Like(String memberId, UUID productId) {
}
