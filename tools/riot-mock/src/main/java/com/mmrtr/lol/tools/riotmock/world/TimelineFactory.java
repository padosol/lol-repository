package com.mmrtr.lol.tools.riotmock.world;

import com.mmrtr.lol.domain.match.readmodel.timeline.ChampionStatsDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.DamageStatsDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.EventsTimeLineDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.FramesTimeLineDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.InfoTimeLineDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.MetadataTimeLineDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.ParticipantFrameDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.ParticipantFramesDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.ParticipantTimeLineDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.PositionDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import java.util.ArrayList;
import java.util.List;

/** 60초 간격 프레임 31개(0~30분)와 참가자 1~10 의 participantFrames 를 만든다. */
final class TimelineFactory {

    private static final int FRAME_COUNT = 31;
    private static final int FRAME_MILLIS = 60_000;

    TimelineDto timeline(MatchRef ref, List<String> puuids, long gameId, int[] championIndexes) {
        MetadataTimeLineDto metadata = new MetadataTimeLineDto();
        metadata.setDataVersion("2");
        metadata.setMatchId(ref.matchId());
        metadata.setParticipants(new ArrayList<>(puuids));

        List<ParticipantTimeLineDto> participants = new ArrayList<>();
        for (int slot = 0; slot < puuids.size(); slot++) {
            ParticipantTimeLineDto p = new ParticipantTimeLineDto();
            p.setParticipantId(slot + 1);
            p.setPuuid(puuids.get(slot));
            participants.add(p);
        }

        List<FramesTimeLineDto> frames = new ArrayList<>();
        for (int minute = 0; minute < FRAME_COUNT; minute++) {
            FramesTimeLineDto frame = new FramesTimeLineDto();
            frame.setTimestamp(minute * FRAME_MILLIS);
            frame.setEvents(events(minute, gameId, championIndexes));
            frame.setParticipantFrames(participantFrames(minute, gameId));
            frames.add(frame);
        }

        InfoTimeLineDto info = new InfoTimeLineDto();
        info.setEndOfGameResult("GameComplete");
        info.setFrameInterval(FRAME_MILLIS);
        info.setGameId(gameId);
        info.setParticipants(participants);
        info.setFrames(frames);

        TimelineDto timeline = new TimelineDto();
        timeline.setMetadata(metadata);
        timeline.setInfo(info);
        return timeline;
    }

    /** 1분 프레임에 구매·스킬 찍기 이벤트를 하나씩 둔다. 그 밖의 프레임은 빈 목록이다. */
    private List<EventsTimeLineDto> events(int minute, long gameId, int[] championIndexes) {
        List<EventsTimeLineDto> events = new ArrayList<>();
        if (minute != 1) {
            return events;
        }
        for (int slot = 0; slot < 10; slot++) {
            int pid = slot + 1;
            long s = LabIds.mix64(gameId * 16 + slot);
            EventsTimeLineDto purchase = new EventsTimeLineDto();
            purchase.setType("ITEM_PURCHASED");
            purchase.setTimestamp(FRAME_MILLIS + 1_000L + slot);
            purchase.setParticipantId(pid);
            purchase.setItemId(LabCatalog.ITEM_IDS[(int) ((s >>> 7) & 0xFFFF) % LabCatalog.ITEM_IDS.length]);
            events.add(purchase);

            EventsTimeLineDto skill = new EventsTimeLineDto();
            skill.setType("SKILL_LEVEL_UP");
            skill.setTimestamp(FRAME_MILLIS + 2_000L + slot);
            skill.setParticipantId(pid);
            skill.setSkillSlot(1 + championIndexes[slot] % 3);
            skill.setLevelUpType("NORMAL");
            events.add(skill);
        }
        return events;
    }

    private ParticipantFramesDto participantFrames(int minute, long gameId) {
        ParticipantFramesDto frames = new ParticipantFramesDto();
        frames.setP1(frame(1, minute, gameId));
        frames.setP2(frame(2, minute, gameId));
        frames.setP3(frame(3, minute, gameId));
        frames.setP4(frame(4, minute, gameId));
        frames.setP5(frame(5, minute, gameId));
        frames.setP6(frame(6, minute, gameId));
        frames.setP7(frame(7, minute, gameId));
        frames.setP8(frame(8, minute, gameId));
        frames.setP9(frame(9, minute, gameId));
        frames.setP10(frame(10, minute, gameId));
        return frames;
    }

    private ParticipantFrameDto frame(int participantId, int minute, long gameId) {
        long s = LabIds.mix64(gameId * 16 + participantId - 1);
        int skill = (int) ((s >>> 9) & 0xFF) % 20;
        int level = Math.min(18, 1 + minute / 2);
        int totalGold = 500 + minute * (300 + skill * 5);

        ChampionStatsDto stats = new ChampionStatsDto();
        stats.setAttackDamage(60 + level * 6);
        stats.setArmor(30 + level * 4);
        stats.setMagicResist(32 + level * 2);
        stats.setHealth(600 + level * 90);
        stats.setHealthMax(600 + level * 90);
        stats.setMovementSpeed(335);

        DamageStatsDto damage = new DamageStatsDto();
        damage.setTotalDamageDone(minute * 700);
        damage.setTotalDamageDoneToChampions(minute * 300);
        damage.setTotalDamageTaken(minute * 500);
        damage.setPhysicalDamageDone(minute * 400);
        damage.setMagicDamageDone(minute * 250);
        damage.setTrueDamageDone(minute * 50);

        PositionDto position = new PositionDto();
        position.setX(1_000 + (participantId * 1_300 + minute * 150) % 13_000);
        position.setY(1_000 + (participantId * 900 + minute * 220) % 13_000);

        ParticipantFrameDto frame = new ParticipantFrameDto();
        frame.setParticipantId(participantId);
        frame.setChampionStats(stats);
        frame.setDamageStats(damage);
        frame.setPosition(position);
        frame.setLevel(level);
        frame.setXp(level * 600);
        frame.setTotalGold(totalGold);
        frame.setCurrentGold(totalGold % 1_500);
        frame.setGoldPerSecond(0);
        frame.setMinionsKilled(minute * (5 + skill % 3));
        frame.setJungleMinionsKilled(participantId % 5 == 2 ? minute * 4 : minute / 3);
        frame.setTimeEnemySpentControlled(0);
        return frame;
    }
}
