package com.mmrtr.lol.infra.persistence.league.entity;

import com.mmrtr.lol.domain.league.domain.TierCutoff;
import jakarta.persistence.Column;
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
                        name = "unique_queue_tier",
                        columnNames = {"queue", "tier"}
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
public class TierCutoffEntity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "queue", nullable = false, length = 50)
    private String queue;

    @Column(name = "tier", nullable = false, length = 20)
    private String tier;

    @Column(name = "min_league_points", nullable = false)
    private int minLeaguePoints;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static TierCutoffEntity fromDomain(TierCutoff domain) {
        return TierCutoffEntity.builder()
                .id(domain.getId())
                .queue(domain.getQueue())
                .tier(domain.getTier())
                .minLeaguePoints(domain.getMinLeaguePoints())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    public TierCutoff toDomain() {
        return TierCutoff.builder()
                .id(this.id)
                .queue(this.queue)
                .tier(this.tier)
                .minLeaguePoints(this.minLeaguePoints)
                .updatedAt(this.updatedAt)
                .build();
    }
}
