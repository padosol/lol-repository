package com.mmrtr.lol.domain.match.application;

import com.mmrtr.lol.domain.match.application.port.MatchQueryPort;
import com.mmrtr.lol.domain.match.application.usecase.MatchQueryUseCase;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.GameInfo;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.Team;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** lol-server {@code MatchService} 조회의 이전. */
@Service
@RequiredArgsConstructor
public class MatchQueryService implements MatchQueryUseCase {

    private final MatchQueryPort matchQueryPort;

    @Override
    public GameReadModel getGame(String matchId) {
        GameInfo gameInfo = matchQueryPort.findGameInfo(matchId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_MATCH_ID));
        List<String> matchIds = List.of(matchId);
        GameReadModel game = GameReadModel.assemble(gameInfo,
                matchQueryPort.findParticipants(matchIds), matchQueryPort.findTimelineEvents(matchIds));
        if (game.teamInfoData() == null) {
            // lol-server 단건 조회는 참가자가 없어도 빈 팀 정보를 둔다 (목록 조회는 null)
            return new GameReadModel(game.gameInfoData(), game.participantData(), new Team(null, null));
        }
        return game;
    }

    @Override
    public MatchTimelineReadModel getTimeline(String matchId) {
        return MatchTimelineReadModel.from(matchQueryPort.findTimelineEvents(List.of(matchId)));
    }
}
