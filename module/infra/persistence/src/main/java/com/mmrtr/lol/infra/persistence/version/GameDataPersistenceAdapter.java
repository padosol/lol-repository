package com.mmrtr.lol.infra.persistence.version;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.domain.gamedata.application.port.PatchNotePersistencePort;
import com.mmrtr.lol.domain.gamedata.application.port.QueuePersistencePort;
import com.mmrtr.lol.domain.gamedata.application.port.SeasonPersistencePort;
import com.mmrtr.lol.domain.gamedata.application.port.VersionPersistencePort;
import com.mmrtr.lol.domain.gamedata.domain.QueueInfo;
import com.mmrtr.lol.domain.gamedata.readmodel.PatchNoteReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.PatchNoteSummaryReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.SeasonReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.VersionReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.patchnote.PatchNoteContent;
import com.mmrtr.lol.infra.persistence.queue_type.QueueEntity;
import com.mmrtr.lol.infra.persistence.queue_type.QueueJpaRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** 게임 데이터 조회 어댑터. 응답 모양은 lol-server gamedata 어댑터·매퍼와 같다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameDataPersistenceAdapter
        implements VersionPersistencePort, SeasonPersistencePort, PatchNotePersistencePort, QueuePersistencePort {

    private static final DateTimeFormatter PATCH_NOTE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final VersionJpaRepository versionJpaRepository;
    private final SeasonJpaRepository seasonJpaRepository;
    private final PatchNoteJpaRepository patchNoteJpaRepository;
    private final QueueJpaRepository queueJpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<VersionReadModel> findLatestVersion() {
        return findRecentVersions(1).stream().findFirst();
    }

    @Override
    public List<VersionReadModel> findRecentVersions(int limit) {
        return versionJpaRepository.findRecentVersions(PageRequest.of(0, limit)).stream()
                .map(this::toReadModel)
                .toList();
    }

    @Override
    public Optional<VersionReadModel> findVersionById(Long versionId) {
        return versionJpaRepository.findById(versionId).map(this::toReadModel);
    }

    @Override
    public List<SeasonReadModel> findAllSeasons() {
        List<SeasonEntity> seasons = seasonJpaRepository.findAllByOrderBySeasonValueDesc();
        Map<Long, List<String>> versionsBySeason = patchVersionsBySeason(
                seasons.stream().map(SeasonEntity::getSeasonId).toList());
        return seasons.stream()
                .map(season -> toReadModel(season, versionsBySeason))
                .toList();
    }

    @Override
    public Optional<SeasonReadModel> findSeasonById(Long seasonId) {
        return seasonJpaRepository.findById(seasonId)
                .map(season -> toReadModel(season, patchVersionsBySeason(List.of(seasonId))));
    }

    @Override
    public List<PatchNoteSummaryReadModel> findAllSummary() {
        return patchNoteJpaRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(entity -> new PatchNoteSummaryReadModel(
                        entity.getVersionId(),
                        entity.getTitle(),
                        entity.getPatchUrl(),
                        formatDate(entity.getCreatedAt())))
                .toList();
    }

    @Override
    public Optional<PatchNoteReadModel> findByVersionId(String versionId) {
        return patchNoteJpaRepository.findById(versionId)
                .map(entity -> new PatchNoteReadModel(
                        entity.getVersionId(),
                        entity.getTitle(),
                        parseContent(entity.getContent()),
                        entity.getPatchUrl(),
                        formatDate(entity.getCreatedAt())));
    }

    @Override
    public List<QueueInfo> findAll() {
        return queueJpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<QueueInfo> findAllTabs() {
        return queueJpaRepository.findAllByIsTab(true).stream().map(this::toDomain).toList();
    }

    private VersionReadModel toReadModel(VersionEntity entity) {
        return new VersionReadModel(
                entity.getVersionId(),
                entity.getVersionValue(),
                entity.getPatchVersionData(),
                entity.getCreatedAt());
    }

    /** 시즌별 패치 버전 값 (최신순). */
    private Map<Long, List<String>> patchVersionsBySeason(List<Long> seasonIds) {
        if (seasonIds.isEmpty()) {
            return Map.of();
        }
        return versionJpaRepository.findAllBySeasonIdInOrderByVersionIdDesc(seasonIds).stream()
                .collect(Collectors.groupingBy(
                        VersionEntity::getSeasonId,
                        Collectors.mapping(VersionEntity::getVersionValue, Collectors.toList())));
    }

    private SeasonReadModel toReadModel(SeasonEntity season, Map<Long, List<String>> versionsBySeason) {
        return new SeasonReadModel(
                season.getSeasonValue(),
                season.getSeasonName(),
                versionsBySeason.getOrDefault(season.getSeasonId(), List.of()));
    }

    private QueueInfo toDomain(QueueEntity entity) {
        return new QueueInfo(entity.getQueueId(), entity.getQueueName(), entity.isTab());
    }

    private String formatDate(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(PATCH_NOTE_DATE) : null;
    }

    private PatchNoteContent parseContent(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(contentJson, PatchNoteContent.class);
        } catch (JsonProcessingException e) {
            // 크롤러가 적재한 행 하나가 깨져도 상세 화면 전체를 500 으로 막지 않는다 — 제목·원문 링크는 내려가고,
            // content 만 비운다. 데이터 이상은 ERROR 로그로 드러낸다.
            log.error("패치노트 content 파싱 실패 - content 를 null 로 응답", e);
            return null;
        }
    }
}
