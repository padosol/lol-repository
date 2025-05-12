package lol.mmrtr.lolrepository.domain.match.entity.timeline.value;

import jakarta.persistence.Embeddable;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ChampionStatsValue {
    private int abilityHaste;
    private int abilityPower;
    private int armor;
    private int armorPen;
    private int armorPenPercent;
    private int attackDamage;
    private int attackSpeed;
    private int bonusArmorPenPercent;
    private int bonusMagicPenPercent;
    private int ccReduction;
    private int cooldownReduction;
    private int health;
    private int healthMax;
    private int healthRegen;
    private int lifesteal;
    private int magicPen;
    private int magicPenPercent;
    private int magicResist;
    private int movementSpeed;
    private int omnivamp;
    private int physicalVamp;
    private int power;
    private int powerMax;
    private int powerRegen;
    private int spellVamp;
}
