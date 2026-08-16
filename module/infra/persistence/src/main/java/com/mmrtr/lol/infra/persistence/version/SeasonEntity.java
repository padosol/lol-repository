package com.mmrtr.lol.infra.persistence.version;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "season")
@EntityListeners(AuditingEntityListener.class)
public class SeasonEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("시즌 ID")
    private Long seasonId;

    @Comment("시즌 번호")
    @Column(name = "season_value", nullable = false, unique = true)
    private int seasonValue;

    @Comment("시즌 이름")
    @Column(name = "season_name", nullable = false, length = 50)
    private String seasonName;

    @Comment("시즌 시작일")
    @Column(name = "start_date")
    private LocalDate startDate;

    @Comment("시즌 종료일")
    @Column(name = "end_date")
    private LocalDate endDate;

    @CreatedDate
    @Comment("생성 일시")
    private LocalDateTime createdAt;
}
