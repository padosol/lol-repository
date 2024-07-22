package lol.mmrtr.lolrepository.dto.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorStatus {

    @JsonProperty(value = "status_code")
    private String statusCode = null;
    private String message;

}
