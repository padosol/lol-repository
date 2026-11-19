package com.mmrtr.lol.domain.spectator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * spectator 컨텍스트 경계 테스트: 도메인은 인프라(어댑터)를 알지 못한다 (헥사고날 도메인 무지성).
 */
class ArchitectureTest {

    private static final List<String> OTHERS = List.of("summoner", "match", "ranking", "champion", "gamedata", "championstats");

    private static final JavaClasses CONTEXT = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.mmrtr.lol.domain.spectator");

    @Test
    void 도메인은_인프라_타입을_모른다() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.mmrtr.lol.domain.spectator..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.mmrtr.lol.infra..",
                        "jakarta.persistence..",
                        "org.springframework.web..",
                        "org.springframework.data.redis..",
                        "org.springframework.amqp..",
                        "org.redisson..");
        rule.check(CONTEXT);
    }

    @Test
    void 다른_컨텍스트는_입구로만_쓴다() {
        String[] ports = OTHERS.stream()
                .map(o -> "com.mmrtr.lol.domain." + o + ".application.port..")
                .toArray(String[]::new);
        String[] services = OTHERS.stream()
                .map(o -> "com.mmrtr.lol.domain." + o + ".application")
                .toArray(String[]::new);
        noClasses().that().resideInAPackage("com.mmrtr.lol.domain.spectator..")
                .should().dependOnClassesThat().resideInAnyPackage(ports)
                .orShould().dependOnClassesThat().resideInAnyPackage(services)
                .check(CONTEXT);
    }
}
