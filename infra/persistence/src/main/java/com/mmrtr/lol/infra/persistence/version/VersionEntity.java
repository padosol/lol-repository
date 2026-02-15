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

    @CreatedDate
    @Comment("생성 일시")
    private LocalDateTime createdAt;

    public VersionEntity(String versionValue) {
        this.versionValue = versionValue;
    }
}
