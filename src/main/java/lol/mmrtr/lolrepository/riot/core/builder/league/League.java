package lol.mmrtr.lolrepository.riot.core.builder.league;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lol.mmrtr.lolrepository.riot.core.api.RiotAPI;
import lol.mmrtr.lolrepository.riot.dto.league.LeagueEntryDTO;
import lol.mmrtr.lolrepository.riot.dto.league.LeagueListDTO;
import lol.mmrtr.lolrepository.riot.type.Division;
import lol.mmrtr.lolrepository.riot.type.Platform;
import lol.mmrtr.lolrepository.riot.type.Tier;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class League {

    private Platform platform;

    public League(Platform platform) {
        this.platform = platform;
    }

    public static class Builder {
        private Platform platform;
        private LeagueTier tier;

        private String summonerId;
        private String leagueId;

        private String puuid;


        public Builder platform(Platform platform) {
            this.platform = platform;
            return this;
        }

        public Builder summonerId(String summonerId) {
            this.summonerId = summonerId;
            return this;
        }

        public Builder leagueId(String leagueId) {
            this.leagueId = leagueId;
            return this;
        }

        public Builder leagueTier(LeagueTier tier) {
            this.tier = tier;
            return this;
        }

        public Builder puuid(String puuid) {
            this.puuid = puuid;
            return this;
        }

        public Set<LeagueEntryDTO> getLeagueEntry()  {

            URI uri = UriComponentsBuilder.newInstance()
                    .scheme("https")
                    .host(RiotAPI.createRegionPath(this.platform))
                    .path("/lol/league/v4/entries/by-summoner/" + this.summonerId)
                    .build()
                    .toUri();
            try {
                ObjectMapper mapper = new ObjectMapper();
                Object[] objects = RiotAPI.getExecute().execute(Object[].class, uri).get();
                Set<LeagueEntryDTO> result = new HashSet<>();
                for (Object object : objects) {

                    String objectToJson = mapper.writeValueAsString(object);
                    LeagueEntryDTO leagueEntryDTO = mapper.readValue(objectToJson, LeagueEntryDTO.class);

                    result.add(leagueEntryDTO);
                }
                return result;
            } catch(ExecutionException | InterruptedException e) {
                throw new IllegalStateException();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        public Set<LeagueEntryDTO> getLeagueEntryByPuuid()  {

            URI uri = UriComponentsBuilder.newInstance()
                    .scheme("https")
                    .host(RiotAPI.createRegionPath(this.platform))
                    .path("/lol/league/v4/entries/by-puuid/" + this.puuid)
                    .build()
                    .toUri();
            try {
                ObjectMapper mapper = new ObjectMapper();
                Object[] objects = RiotAPI.getExecute().execute(Object[].class, uri).get();
                Set<LeagueEntryDTO> result = new HashSet<>();
                for (Object object : objects) {

                    String objectToJson = mapper.writeValueAsString(object);
                    LeagueEntryDTO leagueEntryDTO = mapper.readValue(objectToJson, LeagueEntryDTO.class);

                    result.add(leagueEntryDTO);
                }
                return result;
            } catch(ExecutionException | InterruptedException e) {
                throw new IllegalStateException();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        public LeagueListDTO getLeagueList() {

            URI uri = UriComponentsBuilder.newInstance()
                    .scheme("https")
                    .host(RiotAPI.createRegionPath(this.platform))
                    .path("/lol/league/v4/leagues/" + this.leagueId)
                    .build()
                    .toUri();

            try {
                return RiotAPI.getExecute().execute(LeagueListDTO.class, uri).get();
            } catch(ExecutionException | InterruptedException e) {
                throw new IllegalStateException();
            }
        }

    }

    public Set<LeagueEntryDTO> bySummonerId(String summonerId) {
        return new Builder().summonerId(summonerId).platform(this.platform).getLeagueEntry();
    }

    public Set<LeagueEntryDTO> byPuuid(String puuid) {
        return new Builder().puuid(puuid).platform(this.platform).getLeagueEntryByPuuid();
    }

    public LeagueListDTO byLeagueId(String leagueId) {
        return new Builder().leagueId(leagueId).platform(this.platform).getLeagueList();
    }

    public void byLeagueTier(LeagueTier tier) {

    }

    public void entries(Tier tier, Division division, Queue queue) {

    }






}
