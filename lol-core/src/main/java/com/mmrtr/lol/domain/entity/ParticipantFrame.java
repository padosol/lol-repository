package com.mmrtr.lol.domain.entity;

import com.mmrtr.lol.riot.dto.match_timeline.ParticipantFrameDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParticipantFrame {

    private String matchId;
    private int timestamp;
    private int participantId;

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

    private int currentGold;

    private int magicDamageDone;
    private int magicDamageDoneToChampions;
    private int magicDamageTaken;
    private int physicalDamageDone;
    private int physicalDamageDoneToChampions;
    private int physicalDamageTaken;
    private int totalDamageDone;
    private int totalDamageDoneToChampions;
    private int totalDamageTaken;
    private int trueDamageDone;
    private int trueDamageDoneToChampions;
    private int trueDamageTaken;

    private int goldPerSecond;
    private int jungleMinionsKilled;
    private int level;
    private int minionsKilled;

    private int x;
    private int y;

    private int timeEnemySpentControlled;
    private int totalGold;
    private int xp;


    public ParticipantFrame(){};

    public ParticipantFrame(
            String matchId,
            int timestamp,
            ParticipantFrameDto participantFrameDto
    ) {

        this.matchId= matchId;
        this.timestamp= timestamp;
        this.participantId= participantFrameDto.getParticipantId();


        this.abilityHaste= participantFrameDto.getChampionStats().getAbilityHaste();
        this.abilityPower= participantFrameDto.getChampionStats().getAbilityPower();
        this.armor= participantFrameDto.getChampionStats().getArmor();
        this.armorPen= participantFrameDto.getChampionStats().getArmorPen();
        this.armorPenPercent= participantFrameDto.getChampionStats().getArmorPenPercent();
        this.attackDamage= participantFrameDto.getChampionStats().getAttackDamage();
        this.attackSpeed= participantFrameDto.getChampionStats().getAttackSpeed();
        this.bonusArmorPenPercent= participantFrameDto.getChampionStats().getBonusArmorPenPercent();
        this.bonusMagicPenPercent= participantFrameDto.getChampionStats().getBonusMagicPenPercent();
        this.ccReduction= participantFrameDto.getChampionStats().getCcReduction();
        this.cooldownReduction= participantFrameDto.getChampionStats().getCooldownReduction();
        this.health= participantFrameDto.getChampionStats().getHealth();
        this.healthMax= participantFrameDto.getChampionStats().getHealthMax();
        this.healthRegen= participantFrameDto.getChampionStats().getHealthRegen();
        this.lifesteal= participantFrameDto.getChampionStats().getLifesteal();
        this.magicPen= participantFrameDto.getChampionStats().getMagicPen();
        this.magicPenPercent= participantFrameDto.getChampionStats().getMagicPenPercent();
        this.magicResist= participantFrameDto.getChampionStats().getMagicResist();
        this.movementSpeed= participantFrameDto.getChampionStats().getMovementSpeed();
        this.omnivamp= participantFrameDto.getChampionStats().getOmnivamp();
        this.physicalVamp= participantFrameDto.getChampionStats().getPhysicalVamp();
        this.power= participantFrameDto.getChampionStats().getPower();
        this.powerMax= participantFrameDto.getChampionStats().getPowerMax();
        this.powerRegen= participantFrameDto.getChampionStats().getPowerRegen();
        this.spellVamp= participantFrameDto.getChampionStats().getSpellVamp();
        this.currentGold= participantFrameDto.getCurrentGold();
        this.magicDamageDone= participantFrameDto.getDamageStats().getMagicDamageDone();
        this.magicDamageDoneToChampions= participantFrameDto.getDamageStats().getMagicDamageDoneToChampions();
        this.magicDamageTaken= participantFrameDto.getDamageStats().getMagicDamageTaken();
        this.physicalDamageDone= participantFrameDto.getDamageStats().getPhysicalDamageDone();
        this.physicalDamageDoneToChampions= participantFrameDto.getDamageStats().getPhysicalDamageDoneToChampions();
        this.physicalDamageTaken= participantFrameDto.getDamageStats().getPhysicalDamageTaken();
        this.totalDamageDone= participantFrameDto.getDamageStats().getTotalDamageDone();
        this.totalDamageDoneToChampions= participantFrameDto.getDamageStats().getTotalDamageDoneToChampions();
        this.totalDamageTaken= participantFrameDto.getDamageStats().getTotalDamageTaken();
        this.trueDamageDone= participantFrameDto.getDamageStats().getTrueDamageDone();
        this.trueDamageDoneToChampions= participantFrameDto.getDamageStats().getTrueDamageDoneToChampions();
        this.trueDamageTaken= participantFrameDto.getDamageStats().getTrueDamageTaken();
        this.goldPerSecond= participantFrameDto.getGoldPerSecond();
        this.jungleMinionsKilled= participantFrameDto.getJungleMinionsKilled();
        this.level= participantFrameDto.getLevel();
        this.minionsKilled= participantFrameDto.getMinionsKilled();
        this.x= participantFrameDto.getPosition().getX();
        this.y= participantFrameDto.getPosition().getY();
        this.timeEnemySpentControlled= participantFrameDto.getTimeEnemySpentControlled();
        this.totalGold= participantFrameDto.getTotalGold();
        this.xp= participantFrameDto.getXp();
    }

}
