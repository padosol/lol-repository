package com.mmrtr.lol.domain.summoner.application.port;

import com.mmrtr.lol.domain.summoner.domain.RenewalRecord;
import com.mmrtr.lol.domain.summoner.domain.RenewalStep;
import com.mmrtr.lol.domain.summoner.domain.RenewalTransition;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 갱신 레코드 Port. 쓰기는 모두 renewalId 가 같을 때만 반영하고, 아니면 아무것도 하지 않는다.
 * 시각 비교는 저장소 시계로 한다.
 */
public interface RenewalRecordPort {

    String DEADLINE_REASON = "deadline";

    /** 레코드를 새로 쓰고(덮어씀) 접수 시각(ms)을 돌려준다. 기한은 접수 + 5초. */
    long open(String puuid, String renewalId, String platform);

    Optional<RenewalRecord> find(String puuid);

    RenewalTransition markSteps(String puuid, String renewalId, Set<RenewalStep> steps);

    RenewalTransition fail(String puuid, String renewalId, String reason);

    /** 저장소 시각이 기한을 넘었을 때만 {@code fail(..., "deadline")} 과 같다. 아니면 NONE. */
    RenewalTransition failIfOverdue(String puuid, String renewalId);

    void saveRiotRevisionDate(String puuid, String renewalId, long revisionDateMillis);

    List<String> findActive();

    void deactivate(String puuid);
}
