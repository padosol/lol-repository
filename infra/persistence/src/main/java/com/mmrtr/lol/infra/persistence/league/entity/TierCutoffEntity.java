package com.mmrtr.lol.infra.persistence.league.entity;

import com.mmrtr.lol.domain.league.domain.TierCutoff;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "tier_cutoff",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "unique_queue_tier_region",
                        columnNames = {"queue", "tier", "region"}
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
public class TierCutoffEntity {

    @Id
    @Comment("티어 컷오프 ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("큐 타입")
    private String queue;

    @Comment("티어")
    private String tier;

    @Comment("서버 지역")
    private String region;

    @Comment("최소 리그 포인트")
    private int minLeaguePoints;

    @Comment("LP 변동량")
    private int lpChange;

    @Comment("유저 수")
    private int userCount;

    @Comment("수정 일시")
    private LocalDateTime updatedAt;

    @CreatedDate
    @Comment("생성 일시")
    private LocalDateTime createdAt;

    public static TierCutoffEntity fromDomain(TierCutoff domain) {
        return TierCutoffEntity.builder()
                .id(domain.getId())
                .queue(domain.getQueue())
                .tier(domain.getTier())
                .region(domain.getRegion())
                .minLeaguePoints(domain.getMinLeaguePoints())
                .lpChange(domain.getLpChange())
                .userCount(domain.getUserCount())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    public TierCutoff toDomain() {
        return TierCutoff.builder()
                .id(this.id)
                .queue(this.queue)
                .tier(this.tier)
                .region(this.region)
                .minLeaguePoints(this.minLeaguePoints)
                .lpChange(this.lpChange)
                .userCount(this.userCount)
                .updatedAt(this.updatedAt)
                .build();
    }
}
