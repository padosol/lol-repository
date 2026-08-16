package com.mmrtr.lol.infra.persistence.league.repository;

import com.mmrtr.lol.infra.persistence.config.JpaConfig;
import com.mmrtr.lol.infra.persistence.league.entity.SummonerRankingBackupEntity;
import com.mmrtr.lol.infra.persistence.league.entity.SummonerRankingEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SummonerRankingJpaRepositoryTest {

    @Autowired
    private SummonerRankingJpaRepository summonerRankingJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery("DELETE FROM summoner_ranking_backup").executeUpdate();
        summonerRankingJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("backupCurrentRanks - 큐별 백업 정상 동작")
    void backupCurrentRanks_shouldCopyToBackup() {
        // given
        SummonerRankingEntity entity1 = createRankingEntity("puuid-1", "RANKED_SOLO_5x5", "KR", 1, "Player1");
        SummonerRankingEntity entity2 = createRankingEntity("puuid-2", "RANKED_SOLO_5x5", "KR", 2, "Player2");
        SummonerRankingEntity entity3 = createRankingEntity("puuid-3", "RANKED_FLEX_SR", "KR", 1, "Player3");

        summonerRankingJpaRepository.saveAll(List.of(entity1, entity2, entity3));
        entityManager.flush();
        entityManager.clear();

        // when
        summonerRankingJpaRepository.backupCurrentRanks("RANKED_SOLO_5x5");
        entityManager.flush();
        entityManager.clear();

        // then
        List<SummonerRankingBackupEntity> backups = entityManager
                .createQuery("SELECT b FROM SummonerRankingBackupEntity b ORDER BY b.currentRank", SummonerRankingBackupEntity.class)
                .getResultList();

        assertThat(backups).hasSize(2);

        SummonerRankingBackupEntity backup1 = backups.get(0);
        assertThat(backup1.getPuuid()).isEqualTo("puuid-1");
        assertThat(backup1.getQueue()).isEqualTo("RANKED_SOLO_5x5");
        assertThat(backup1.getPlatformId()).isEqualTo("KR");
        assertThat(backup1.getCurrentRank()).isEqualTo(1);

        SummonerRankingBackupEntity backup2 = backups.get(1);
        assertThat(backup2.getPuuid()).isEqualTo("puuid-2");
        assertThat(backup2.getCurrentRank()).isEqualTo(2);
    }

    @Test
    @DisplayName("updateRankChangesFromBackup - 순위 변동 계산")
    void updateRankChangesFromBackup_shouldCalculateRankChange() {
        // given: 이전 순위 백업
        SummonerRankingEntity entity = createRankingEntity("puuid-1", "RANKED_SOLO_5x5", "KR", 5, "Player1");

        summonerRankingJpaRepository.save(entity);
        entityManager.flush();

        // 현재 순위를 백업 (이전 순위 = 5)
        summonerRankingJpaRepository.backupCurrentRanks("RANKED_SOLO_5x5");
        entityManager.flush();

        // 새 순위로 데이터 삭제 후 재삽입 (새 순위 = 3, 2단계 상승)
        summonerRankingJpaRepository.deleteByQueue("RANKED_SOLO_5x5");
        entityManager.flush();
        entityManager.clear();

        SummonerRankingEntity newEntity = createRankingEntity("puuid-1", "RANKED_SOLO_5x5", "KR", 3, "Player1");
        summonerRankingJpaRepository.save(newEntity);
        entityManager.flush();
        entityManager.clear();

        // when
        summonerRankingJpaRepository.updateRankChangesFromBackup("RANKED_SOLO_5x5");
        entityManager.flush();
        entityManager.clear();

        // then: rankChange = backup.current_rank(5) - new.current_rank(3) = 2
        SummonerRankingEntity result = summonerRankingJpaRepository.findAll().get(0);
        assertThat(result.getRankChange()).isEqualTo(2);
    }

    @Test
    @DisplayName("clearBackup - 백업 데이터 정리")
    void clearBackup_shouldDeleteBackupByQueue() {
        // given
        SummonerRankingEntity entity = createRankingEntity("puuid-1", "RANKED_SOLO_5x5", "KR", 1, "Player1");

        summonerRankingJpaRepository.save(entity);
        entityManager.flush();

        summonerRankingJpaRepository.backupCurrentRanks("RANKED_SOLO_5x5");
        entityManager.flush();
        entityManager.clear();

        // when
        summonerRankingJpaRepository.clearBackup("RANKED_SOLO_5x5");
        entityManager.flush();
        entityManager.clear();

        // then
        Long count = (Long) entityManager
                .createQuery("SELECT COUNT(b) FROM SummonerRankingBackupEntity b")
                .getSingleResult();
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("backupCurrentRanks - 다중 플랫폼 백업")
    void backupCurrentRanks_multiPlatform_shouldCopyAllPlatforms() {
        // given
        SummonerRankingEntity krEntity = createRankingEntity("puuid-kr", "RANKED_SOLO_5x5", "KR", 1, "KRPlayer");
        SummonerRankingEntity naEntity = SummonerRankingEntity.builder()
                .puuid("puuid-na")
                .queue("RANKED_SOLO_5x5")
                .platformId("NA1")
                .currentRank(1)
                .rankChange(0)
                .gameName("NAPlayer")
                .tagLine("NA1")
                .wins(80)
                .losses(60)
                .winRate(BigDecimal.valueOf(57.14))
                .tier("GRANDMASTER")
                .rank("I")
                .leaguePoints(700)
                .build();

        summonerRankingJpaRepository.saveAll(List.of(krEntity, naEntity));
        entityManager.flush();
        entityManager.clear();

        // when
        summonerRankingJpaRepository.backupCurrentRanks("RANKED_SOLO_5x5");
        entityManager.flush();
        entityManager.clear();

        // then
        List<SummonerRankingBackupEntity> backups = entityManager
                .createQuery("SELECT b FROM SummonerRankingBackupEntity b ORDER BY b.platformId", SummonerRankingBackupEntity.class)
                .getResultList();

        assertThat(backups).hasSize(2);
        assertThat(backups).extracting(SummonerRankingBackupEntity::getPlatformId)
                .containsExactly("KR", "NA1");
    }

    private SummonerRankingEntity createRankingEntity(String puuid, String queue, String platformId, int rank, String gameName) {
        return SummonerRankingEntity.builder()
                .puuid(puuid)
                .queue(queue)
                .platformId(platformId)
                .currentRank(rank)
                .rankChange(0)
                .gameName(gameName)
                .tagLine("KR1")
                .wins(100)
                .losses(50)
                .winRate(BigDecimal.valueOf(66.67))
                .tier("CHALLENGER")
                .rank("I")
                .leaguePoints(1000)
                .build();
    }
}
