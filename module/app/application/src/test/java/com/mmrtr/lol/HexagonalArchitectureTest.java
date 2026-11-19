package com.mmrtr.lol;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HexagonalArchitectureTest {

    private static final String PERSISTENCE = "com.mmrtr.lol.infra.persistence.";

    private static final Map<String, String> PERSISTENCE_SLICES = Map.of(
            "summoner", "summoner",
            "match", "match",
            "ranking", "ranking",
            "spectator", "spectator",
            "version", "gamedata",
            "queue_type", "gamedata");

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.mmrtr.lol");

    private static final SliceAssignment PERSISTENCE_CONTEXTS = new SliceAssignment() {
        @Override
        public String getDescription() {
            return "persistence context";
        }

        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
            String name = javaClass.getPackageName() + ".";
            if (!name.startsWith(PERSISTENCE)) {
                return SliceIdentifier.ignore();
            }
            String top = name.substring(PERSISTENCE.length()).split("\\.")[0];
            String slice = PERSISTENCE_SLICES.get(top);
            return slice == null ? SliceIdentifier.ignore() : SliceIdentifier.of(slice);
        }
    };

    @Test
    void infra와_컨트롤러는_UseCase를_구현하지_않는다() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.mmrtr.lol.infra..", "com.mmrtr.lol.controller..")
                .should().implement(JavaClass.Predicates.resideInAPackage("..application.usecase.."));
        rule.check(CLASSES);
    }

    @Test
    void persistence는_컨텍스트끼리_import하지_않는다() {
        ArchRule rule = FreezingArchRule.freeze(
                slices().assignedFrom(PERSISTENCE_CONTEXTS).should().notDependOnEachOther());
        rule.check(CLASSES);
    }
}
