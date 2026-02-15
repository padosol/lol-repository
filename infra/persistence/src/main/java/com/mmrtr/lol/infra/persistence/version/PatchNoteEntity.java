package com.mmrtr.lol.infra.persistence.version;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "patch_note")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatchNoteEntity {

    @Id
    @Comment("패치 버전 ID")
    private String versionId;

    @Comment("패치노트 제목")
    @Column(name = "title", nullable = false)
    private String title;

    @Comment("패치노트 내용 (JSON)")
    @Column(name = "content", columnDefinition = "jsonb")
    private String content;

    @Comment("패치노트 URL")
    private String patchUrl;

    @Comment("생성 일시")
    private LocalDateTime createdAt;
}
