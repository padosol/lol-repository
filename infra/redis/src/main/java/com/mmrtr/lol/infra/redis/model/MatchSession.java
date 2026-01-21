package com.mmrtr.lol.infra.redis.model;

import com.mmrtr.lol.common.type.Platform;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;

@Getter
@Setter
@RedisHash
@NoArgsConstructor
@AllArgsConstructor
public class MatchSession implements Serializable {

    @Id
    private String matchId;

    private Platform platform;
}
