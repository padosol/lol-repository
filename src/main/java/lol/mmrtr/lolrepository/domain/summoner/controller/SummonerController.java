package lol.mmrtr.lolrepository.domain.summoner.controller;

import lol.mmrtr.lolrepository.domain.summoner.dto.response.SummonerResponse;
import lol.mmrtr.lolrepository.domain.summoner.service.SummonerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/riot/{region}/summoners")
@RequiredArgsConstructor
public class SummonerController {

    private final SummonerService summonerService;

    /**
     * 유저 조회 API With Riot API
     * @param region   지역명
     * @param gameName 유저명
     * @param tagLine  유저 태그
     * @return 유저 정보      
     */
    @GetMapping("/{gameName}/{tagLine}")
    public ResponseEntity<SummonerResponse> getSummoner(
            @PathVariable("region") String region,
            @PathVariable("gameName") String gameName,
            @PathVariable("tagLine") String tagLine
    ) {
        SummonerResponse summonerInfo = summonerService.getSummonerInfo(region, gameName, tagLine);

        return ResponseEntity.ok(summonerInfo);
    }
}