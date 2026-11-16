package com.mmrtr.lol.backfill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackfillFactTypeTest {

    @DisplayName("MATCH 의 증분 메타데이터")
    @Test
    void match_incrementalMetadata_matchesTable() {
        // given
        BackfillFactType t = BackfillFactType.MATCH;

        // when / then
        assertThat(t.gcsPrefixFor(ExportMode.INCREMENTAL)).isEqualTo("match/incremental");
        assertThat(t.gcsPrefixFor(ExportMode.BACKFILL)).isEqualTo(t.gcsPrefix());
        assertThat(t.bqTable()).isEqualTo("match_fact");
        assertThat(t.sourceTable()).isEqualTo("match");
        assertThat(t.sourceIdColumn()).isEqualTo("id");
    }

    @DisplayName("MATCH_BAN 의 증분 메타데이터")
    @Test
    void matchBan_incrementalMetadata_matchesTable() {
        // given
        BackfillFactType t = BackfillFactType.MATCH_BAN;

        // when / then
        assertThat(t.gcsPrefixFor(ExportMode.INCREMENTAL)).isEqualTo("match_ban/incremental");
        assertThat(t.gcsPrefixFor(ExportMode.BACKFILL)).isEqualTo(t.gcsPrefix());
        assertThat(t.bqTable()).isEqualTo("match_ban_fact");
        assertThat(t.sourceTable()).isEqualTo("match_ban");
        assertThat(t.sourceIdColumn()).isEqualTo("id");
    }

    @DisplayName("MATCH_PARTICIPANT_BUILD 의 증분 메타데이터")
    @Test
    void matchParticipantBuild_incrementalMetadata_matchesTable() {
        // given
        BackfillFactType t = BackfillFactType.MATCH_PARTICIPANT_BUILD;

        // when / then
        assertThat(t.gcsPrefixFor(ExportMode.INCREMENTAL)).isEqualTo("match_participant_build/incremental");
        assertThat(t.gcsPrefixFor(ExportMode.BACKFILL)).isEqualTo(t.gcsPrefix());
        assertThat(t.bqTable()).isEqualTo("match_participant_fact");
        assertThat(t.sourceTable()).isEqualTo("match_participant");
        assertThat(t.sourceIdColumn()).isEqualTo("match_participant_id");
    }
}
