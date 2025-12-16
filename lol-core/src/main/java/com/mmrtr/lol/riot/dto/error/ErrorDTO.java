package com.mmrtr.lol.riot.dto.error;

import com.mmrtr.lol.riot.dto.error.exception.ExceptionResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorDTO {

    private ErrorStatus status;

    public boolean isError() {
        return status != null;
    }

    public ExceptionResponse toResponse() {
        ExceptionResponse exceptionResponse = new ExceptionResponse();
        exceptionResponse.setStatusCode(this.status.getStatusCode());
        exceptionResponse.setMessage(this.status.getMessage());

        return exceptionResponse;
    }

}
