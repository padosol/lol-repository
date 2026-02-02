package com.mmrtr.lol.infra.persistence.version;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
    @Column(name = "version_id")
    private Long versionId;

    @Column(name = "version_value", nullable = false, length = 20)
    private String versionValue;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public VersionEntity(String versionValue) {
        this.versionValue = versionValue;
    }
}
