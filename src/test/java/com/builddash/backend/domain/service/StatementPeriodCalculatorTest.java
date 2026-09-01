package com.builddash.backend.domain.service;

import com.builddash.backend.domain.service.StatementPeriodCalculator.Period;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatementPeriodCalculatorTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    /** Southern-hemisphere DST zone: offset shifts +10:00 -> +11:00 in October. */
    private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");

    @Test
    void period_halfOpen_boundariesAsUTCDInstants() {
        Period p = StatementPeriodCalculator.period(YearMonth.of(2026, 9), KOLKATA);
        assertThat(p.start()).isEqualTo(Instant.parse("2026-08-31T18:30:00Z")); // 2026-09-01T00:00+05:30
        assertThat(p.end()).isEqualTo(Instant.parse("2026-09-30T18:30:00Z"));   // 2026-10-01T00:00+05:30
        assertThat(p.periodKey()).isEqualTo("202609");
    }

    @Test
    void monthBoundary_exactBoundaryInstantBelongsToNextPeriod() {
        Period september = StatementPeriodCalculator.period(YearMonth.of(2026, 9), KOLKATA);
        Period october = StatementPeriodCalculator.period(YearMonth.of(2026, 10), KOLKATA);
        // [start, end): September's end is October's start; that instant is October's.
        assertThat(september.end()).isEqualTo(october.start());
        assertThat(september.start().compareTo(september.end())).isLessThan(0);
    }

    @Test
    void leapYearFebruary_29Days() {
        Period feb = StatementPeriodCalculator.period(YearMonth.of(2028, 2), KOLKATA);
        Period mar = StatementPeriodCalculator.period(YearMonth.of(2028, 3), KOLKATA);
        assertThat(feb.end()).isEqualTo(mar.start());
        // March 1 local midnight (leap year) = 2028-02-29T18:30Z in +05:30.
        assertThat(feb.end()).isEqualTo(Instant.parse("2028-02-29T18:30:00Z"));
    }

    @Test
    void dstTransition_resolvesViaZoneRules() {
        Period october = StatementPeriodCalculator.period(YearMonth.of(2026, 10), SYDNEY);
        // Local midnight Nov 1 exists in both offsets; the instant is well-defined and
        // equals November's start exactly — no drift, no ambiguity.
        Period november = StatementPeriodCalculator.period(YearMonth.of(2026, 11), SYDNEY);
        assertThat(october.end()).isEqualTo(november.start());
        assertThat(october.start().isBefore(october.end())).isTrue();
    }

    @Test
    void differentCompanyTimezones_produceDifferentBoundaries_sameInstant() {
        Period kolkata = StatementPeriodCalculator.period(YearMonth.of(2026, 9), KOLKATA);
        Period utc = StatementPeriodCalculator.period(YearMonth.of(2026, 9), ZoneId.of("UTC"));
        // The boundary instant 2026-08-31T18:30Z is September in Kolkata but August in UTC.
        assertThat(utc.start().isAfter(kolkata.start())).isTrue();
        assertThat(Instant.parse("2026-08-31T20:00:00Z").isBefore(utc.start())).isTrue();
        assertThat(Instant.parse("2026-08-31T20:00:00Z").isAfter(kolkata.start())).isTrue();
    }

    @Test
    void closedMonths_backfillsUpToTwelveAndExcludesCurrent() {
        // 2026-09-10 in Kolkata: current month = 2026-09; closed = 2025-09 .. 2026-08.
        List<YearMonth> closed = StatementPeriodCalculator.closedMonths(KOLKATA,
                Instant.parse("2026-09-10T05:00:00Z"));
        assertThat(closed).hasSize(12);
        assertThat(closed.get(0)).isEqualTo(YearMonth.of(2025, 9));
        assertThat(closed.get(11)).isEqualTo(YearMonth.of(2026, 8));
        assertThat(closed).doesNotContain(YearMonth.of(2026, 9));
    }

    @Test
    void isClosed_onlyAfterEndBoundary() {
        YearMonth august2026 = YearMonth.of(2026, 8);
        Period p = StatementPeriodCalculator.period(august2026, KOLKATA);
        assertThat(StatementPeriodCalculator.isClosed(august2026, KOLKATA, p.end().minusSeconds(1))).isFalse();
        assertThat(StatementPeriodCalculator.isClosed(august2026, KOLKATA, p.end())).isTrue();
    }
}
