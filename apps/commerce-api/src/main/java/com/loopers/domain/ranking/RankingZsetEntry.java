package com.loopers.domain.ranking;

/**
 * Redis ZSET에서 읽은 member·score 한 쌍
 *
 * @param member 랭킹 멤버
 * @param score 랭킹 점수
 */
public record RankingZsetEntry(String member, double score) {
}
