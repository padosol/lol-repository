package com.mmrtr.lol.infra.riot.dto.error;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorStatus {
    private int statusCode;
    private String message;
}
