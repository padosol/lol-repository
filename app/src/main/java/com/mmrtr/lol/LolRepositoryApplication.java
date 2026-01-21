package com.mmrtr.lol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.mmrtr.lol"
})
public class LolRepositoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(LolRepositoryApplication.class, args);
    }
}
