package com.mmrtr.lol.infra.persistence.version;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "patch_version")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class VersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("버전 ID")
    private Long versionId;

    @Comment("패치 버전 값")
    @Column(name = "version_value", nullable = false, length = 20)
    private String versionValue;

    @Comment("시즌 ID")
    @Column(name = "season_id")
    private Long seasonId;

    /**
     * Data Dragon 정적 데이터 버전 (예: 16.16.1, V35). 핫픽스 때 마지막 자리가 달라질 수 있어
     * {@link #versionValue} 에서 파생하지 않고 저장한다. 과거 행은 null 일 수 있다.
     */
    @Comment("Data Dragon 정적 데이터 버전")
    @Column(name = "patch_version_data", length = 20)
    private String patchVersionData;

    @CreatedDate
    @Comment("생성 일시")
    private LocalDateTime createdAt;

    public VersionEntity(String versionValue) {
        this.versionValue = versionValue;
    }
}
