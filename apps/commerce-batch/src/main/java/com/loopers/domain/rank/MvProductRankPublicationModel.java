package com.loopers.domain.rank;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "mv_product_rank_publication")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvProductRankPublicationModel {

    @EmbeddedId
    private MvProductRankPublicationId id;

    @Column(name = "published_version", nullable = false)
    private Long publishedVersion;

    @Column(name = "next_version", nullable = false)
    private Long nextVersion;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
