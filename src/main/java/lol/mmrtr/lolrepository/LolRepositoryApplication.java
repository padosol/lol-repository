package lol.mmrtr.lolrepository;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class LolRepositoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(LolRepositoryApplication.class, args);
    }

}
