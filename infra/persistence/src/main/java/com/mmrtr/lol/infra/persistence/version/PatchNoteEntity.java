package com.mmrtr.lol.infra.persistence.version;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "patch_note")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatchNoteEntity {

    @Id
    @Column(name = "version_id")
    private String versionId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "jsonb")
    private String content;

    @Column(name = "patch_url")
    private String patchUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
