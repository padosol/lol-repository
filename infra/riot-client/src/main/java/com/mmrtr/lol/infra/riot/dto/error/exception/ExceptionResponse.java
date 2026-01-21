package com.mmrtr.lol.infra.riot.dto.error.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExceptionResponse {
    private int statusCode;
    private String message;
}
