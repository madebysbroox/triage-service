package com.example.triage.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogSlicingServiceTest {
    @Test
    void shortLogIsKeptWholeWithOriginalLineNumbers() {
        LogSlice slice = new LogSlicingService(TestProperties.defaults()).slice("INFO a\n\nERROR b\n");
        assertThat(slice.lines()).extracting(LogLine::number).containsExactly(1, 3);
        assertThat(slice.totalLines()).isEqualTo(2);
    }

    @Test
    void longLogKeepsOnlyTheWindowAroundTheError() {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            lines.add(i == 150 ? "ERROR NoSuchKey: fig1.tif" : "DEBUG heartbeat " + i);
        }
        LogSlice slice = new LogSlicingService(TestProperties.withMaxLines(20)).slice(String.join("\n", lines));

        assertThat(slice.lines()).extracting(LogLine::number).containsExactly(148, 149, 150, 151, 152);
        assertThat(slice.totalLines()).isEqualTo(200);
        assertThat(slice.text()).contains("150: ERROR NoSuchKey");
    }

    @Test
    void longLogWithNoSignalKeepsTheTail() {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            lines.add("INFO step " + i);
        }
        LogSlice slice = new LogSlicingService(TestProperties.withMaxLines(10)).slice(String.join("\n", lines));
        assertThat(slice.lines()).extracting(LogLine::number).startsWith(41).endsWith(50).hasSize(10);
    }

    @Test
    void emptyLogGivesEmptySliceAndPlaceholderEvidence() {
        LogSlice slice = new LogSlicingService(TestProperties.defaults()).slice("  ");
        assertThat(slice.lines()).isEmpty();
        assertThat(slice.firstRelevantEvidence().getFirst().text()).contains("No log lines supplied");
    }
}
