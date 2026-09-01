package com.builddash.backend.domain.service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Statement period math (9-E). Pure — no ports, no JVM-default timezone ever consulted.
 * A period is the local calendar month of the company's business timezone, persisted as
 * the UTC instants of its first-instant boundaries: [start, end). Boundary math goes
 * through ZonedDateTime so DST-shifted local midnights resolve by zone rules.
 */
public final class StatementPeriodCalculator {

    /** How far back the scheduler looks for missed closed months (bounded sweeps). */
    public static final int MAX_BACKFILL_MONTHS = 12;

    private StatementPeriodCalculator() {
    }

    public static Period period(YearMonth month, ZoneId zone) {
        Instant start = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        // YYYYMM — the statement-number and period-key format (ST-YYYYMM-####).
        String periodKey = String.format("%d%02d", month.getYear(), month.getMonthValue());
        return new Period(start, end, periodKey);
    }

    /** One closed-month window: UTC boundary instants + the YYYYMM key. */
    public record Period(Instant start, Instant end, String periodKey) {
    }

    /** A month is closed once `now` has passed its end boundary in the company tz. */
    public static boolean isClosed(YearMonth month, ZoneId zone, Instant now) {
        return !now.isBefore(period(month, zone).end());
    }

    /**
     * All closed months (oldest first) within MAX_BACKFILL_MONTHS of the current local
     * month. Application-down gaps and late startups are covered by construction —
     * every missing closed month is returned, not just the previous one.
     */
    public static List<YearMonth> closedMonths(ZoneId zone, Instant now) {
        YearMonth current = YearMonth.from(now.atZone(zone));
        List<YearMonth> closed = new ArrayList<>();
        for (int i = MAX_BACKFILL_MONTHS; i >= 1; i--) {
            closed.add(current.minusMonths(i));
        }
        return closed;
    }
}
