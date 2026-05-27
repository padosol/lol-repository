package com.mmrtr.lol.infra.riot.dto.account;

import com.mmrtr.lol.infra.riot.dto.error.ErrorDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AccountDto extends ErrorDTO {
    private String puuid;
    private String gameName;
    private String tagLine;
}
