package com.mmrtr.lol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.mmrtr.lol"
})
@EnableScheduling
public class LolRepositoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(LolRepositoryApplication.class, args);
    }
}
