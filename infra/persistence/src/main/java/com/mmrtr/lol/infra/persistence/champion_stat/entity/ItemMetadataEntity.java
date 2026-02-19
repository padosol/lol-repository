package com.mmrtr.lol.infra.persistence.champion_stat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "item_metadata")
public class ItemMetadataEntity {

    @Id
    @Comment("RIOT 아이템 ID")
    @Column(name = "item_id")
    private int itemId;

    @Comment("아이템 이름")
    private String itemName;

    @Comment("아이템 분류")
    private String itemCategory;

    @Comment("적용 패치 버전")
    private String gameVersion;
}
