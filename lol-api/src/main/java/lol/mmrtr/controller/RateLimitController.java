package lol.mmrtr.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RateLimitController {

    @GetMapping("/api/rateLimit")
    public String rateLimit() {
        return "ok";
    }
}
