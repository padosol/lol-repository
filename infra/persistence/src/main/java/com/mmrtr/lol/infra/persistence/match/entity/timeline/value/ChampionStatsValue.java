package com.mmrtr.lol.infra.persistence.match.entity.timeline.value;

import jakarta.persistence.Embeddable;
import lombok.*;
import org.hibernate.annotations.Comment;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ChampionStatsValue {
    @Comment("능력 가속")
    private int abilityHaste;
    @Comment("주문력")
    private int abilityPower;
    @Comment("방어력")
    private int armor;
    @Comment("방어 관통력")
    private int armorPen;
    @Comment("방어 관통력 (%)")
    private int armorPenPercent;
    @Comment("공격력")
    private int attackDamage;
    @Comment("공격 속도")
    private int attackSpeed;
    @Comment("추가 방어 관통력 (%)")
    private int bonusArmorPenPercent;
    @Comment("추가 마법 관통력 (%)")
    private int bonusMagicPenPercent;
    @Comment("CC 감소")
    private int ccReduction;
    @Comment("쿨다운 감소")
    private int cooldownReduction;
    @Comment("현재 체력")
    private int health;
    @Comment("최대 체력")
    private int healthMax;
    @Comment("체력 재생")
    private int healthRegen;
    @Comment("생명력 흡수")
    private int lifesteal;
    @Comment("마법 관통력")
    private int magicPen;
    @Comment("마법 관통력 (%)")
    private int magicPenPercent;
    @Comment("마법 저항력")
    private int magicResist;
    @Comment("이동 속도")
    private int movementSpeed;
    @Comment("모든 피해 흡혈")
    private int omnivamp;
    @Comment("물리 피해 흡혈")
    private int physicalVamp;
    @Comment("자원량")
    private int power;
    @Comment("최대 자원량")
    private int powerMax;
    @Comment("자원 재생")
    private int powerRegen;
    @Comment("주문 흡혈")
    private int spellVamp;
}
