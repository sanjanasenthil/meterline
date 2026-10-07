package com.meterline.billing;

import com.meterline.pricing.BillingPeriod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class BillingRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final Clock clock;

    public BillingRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbcTemplate) {
        this(jdbcTemplate, namedJdbcTemplate, Clock.systemUTC());
    }

    BillingRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.clock = clock;
    }

    public void ensureOpenPeriod(BillingPeriod period) {
        String sql = """
                INSERT INTO billing_periods (
                    period_id,
                    period_start,
                    period_end,
                    status,
                    closed_at
                )
                VALUES (
                    :periodId,
                    :periodStart,
                    :periodEnd,
                    'OPEN',
                    NULL
                )
                ON CONFLICT (period_start, period_end) DO NOTHING
                """;

        namedJdbcTemplate.update(sql, periodParameters(period)
                .addValue("periodId", StableBillingIds.periodId(period.startInclusive(), period.endExclusive())));
    }

    public void closePeriod(BillingPeriod period) {
        upsertPeriod(period, BillingPeriodStatus.CLOSED, Instant.now(clock));
    }

    public Optional<BillingPeriodStatus> periodStatus(BillingPeriod period) {
        String sql = """
                SELECT status
                FROM billing_periods
                WHERE period_start = ?
                  AND period_end = ?
                """;
        return jdbcTemplate.query(sql,
                        (rs, rowNum) -> BillingPeriodStatus.valueOf(rs.getString("status")),
                        Timestamp.from(period.startInclusive()),
                        Timestamp.from(period.endExclusive()))
                .stream()
                .findFirst();
    }

    public int aggregateUsage(BillingPeriod period) {
        String sql = """
                INSERT INTO usage_aggregates (
                    aggregate_id,
                    customer_id,
                    meter_id,
                    period_start,
                    period_end,
                    quantity_units,
                    source_event_count
                )
                SELECT CONCAT(
                           'agg_',
                           md5(customer_id || '|' || meter_id || '|' || CAST(:periodStart AS text) || '|' || CAST(:periodEnd AS text))
                       ) AS aggregate_id,
                       customer_id,
                       meter_id,
                       :periodStart AS period_start,
                       :periodEnd AS period_end,
                       COALESCE(SUM(quantity_units), 0) AS quantity_units,
                       COUNT(*) AS source_event_count
                FROM raw_usage_events
                WHERE event_timestamp >= :periodStart
                  AND event_timestamp < :periodEnd
                  AND event_type IN ('USAGE', 'ADJUSTMENT')
                GROUP BY customer_id, meter_id
                ON CONFLICT (customer_id, meter_id, period_start, period_end)
                DO UPDATE SET
                    quantity_units = EXCLUDED.quantity_units,
                    source_event_count = EXCLUDED.source_event_count,
                    updated_at = now()
                """;

        return namedJdbcTemplate.update(sql, periodParameters(period));
    }

    public List<UsageAggregate> aggregatesFor(BillingPeriod period) {
        String sql = """
                SELECT aggregate_id,
                       customer_id,
                       meter_id,
                       period_start,
                       period_end,
                       quantity_units,
                       source_event_count
                FROM usage_aggregates
                WHERE period_start = ?
                  AND period_end = ?
                ORDER BY customer_id, meter_id
                """;

        return jdbcTemplate.query(
                sql,
                usageAggregateMapper(),
                Timestamp.from(period.startInclusive()),
                Timestamp.from(period.endExclusive()));
    }

    public String upsertInvoice(String invoiceId, String customerId, BillingPeriod period) {
        String sql = """
                INSERT INTO invoices (
                    invoice_id,
                    customer_id,
                    period_start,
                    period_end,
                    status
                )
                VALUES (
                    :invoiceId,
                    :customerId,
                    :periodStart,
                    :periodEnd,
                    'DRAFT'
                )
                ON CONFLICT (customer_id, period_start, period_end)
                DO UPDATE SET updated_at = now()
                RETURNING invoice_id
                """;

        return namedJdbcTemplate.queryForObject(
                sql,
                periodParameters(period)
                        .addValue("invoiceId", invoiceId)
                        .addValue("customerId", customerId),
                String.class);
    }

    public int upsertUsageLine(
            String lineId,
            String invoiceId,
            UsageAggregate aggregate,
            long amountCents,
            long rateMillionthsOfCent
    ) {
        String sql = """
                INSERT INTO invoice_lines (
                    line_id,
                    invoice_id,
                    customer_id,
                    meter_id,
                    period_start,
                    period_end,
                    line_type,
                    quantity_units,
                    amount_cents,
                    rate_millionths_of_cent,
                    source_aggregate_id,
                    description
                )
                VALUES (
                    :lineId,
                    :invoiceId,
                    :customerId,
                    :meterId,
                    :periodStart,
                    :periodEnd,
                    'USAGE',
                    :quantityUnits,
                    :amountCents,
                    :rateMillionthsOfCent,
                    :sourceAggregateId,
                    :description
                )
                ON CONFLICT (line_id)
                DO UPDATE SET
                    quantity_units = EXCLUDED.quantity_units,
                    amount_cents = EXCLUDED.amount_cents,
                    rate_millionths_of_cent = EXCLUDED.rate_millionths_of_cent,
                    updated_at = now()
                """;

        return namedJdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("lineId", lineId)
                .addValue("invoiceId", invoiceId)
                .addValue("customerId", aggregate.customerId())
                .addValue("meterId", aggregate.meterId())
                .addValue("periodStart", Timestamp.from(aggregate.periodStart()))
                .addValue("periodEnd", Timestamp.from(aggregate.periodEnd()))
                .addValue("quantityUnits", aggregate.quantityUnits())
                .addValue("amountCents", amountCents)
                .addValue("rateMillionthsOfCent", rateMillionthsOfCent)
                .addValue("sourceAggregateId", aggregate.aggregateId())
                .addValue("description", "Usage for %s".formatted(aggregate.meterId())));
    }

    public int upsertAdjustmentLine(
            String lineId,
            String invoiceId,
            BillingPeriod targetPeriod,
            LateUsageEvent lateEvent,
            long amountCents,
            long rateMillionthsOfCent
    ) {
        String sql = """
                INSERT INTO invoice_lines (
                    line_id,
                    invoice_id,
                    customer_id,
                    meter_id,
                    period_start,
                    period_end,
                    line_type,
                    quantity_units,
                    amount_cents,
                    rate_millionths_of_cent,
                    adjustment_for_event_id,
                    description
                )
                VALUES (
                    :lineId,
                    :invoiceId,
                    :customerId,
                    :meterId,
                    :periodStart,
                    :periodEnd,
                    'ADJUSTMENT',
                    :quantityUnits,
                    :amountCents,
                    :rateMillionthsOfCent,
                    :adjustmentForEventId,
                    :description
                )
                ON CONFLICT (line_id)
                DO UPDATE SET
                    quantity_units = EXCLUDED.quantity_units,
                    amount_cents = EXCLUDED.amount_cents,
                    rate_millionths_of_cent = EXCLUDED.rate_millionths_of_cent,
                    updated_at = now()
                """;

        return namedJdbcTemplate.update(sql, periodParameters(targetPeriod)
                .addValue("lineId", lineId)
                .addValue("invoiceId", invoiceId)
                .addValue("customerId", lateEvent.customerId())
                .addValue("meterId", lateEvent.meterId())
                .addValue("quantityUnits", lateEvent.quantityUnits())
                .addValue("amountCents", amountCents)
                .addValue("rateMillionthsOfCent", rateMillionthsOfCent)
                .addValue("adjustmentForEventId", lateEvent.eventId())
                .addValue("description", "Late event adjustment for %s".formatted(lateEvent.eventId())));
    }

    public List<LateUsageEvent> lateEventsForAdjustment(BillingPeriod targetPeriod) {
        String sql = """
                SELECT e.event_id,
                       e.customer_id,
                       e.meter_id,
                       e.quantity_units,
                       e.event_timestamp,
                       p.period_start AS original_period_start,
                       p.period_end AS original_period_end
                FROM raw_usage_events e
                JOIN billing_periods p
                  ON e.event_timestamp >= p.period_start
                 AND e.event_timestamp < p.period_end
                WHERE p.status = 'CLOSED'
                  AND p.closed_at IS NOT NULL
                  AND e.received_at > p.closed_at
                  AND e.received_at >= :periodStart
                  AND e.received_at < :periodEnd
                  AND e.event_type IN ('USAGE', 'ADJUSTMENT')
                ORDER BY e.customer_id, e.meter_id, e.event_timestamp, e.event_id
                """;

        return namedJdbcTemplate.query(sql, periodParameters(targetPeriod), lateEventMapper());
    }

    public void refreshInvoiceTotal(String invoiceId) {
        String sql = """
                UPDATE invoices
                SET total_cents = (
                        SELECT COALESCE(SUM(amount_cents), 0)
                        FROM invoice_lines
                        WHERE invoice_id = :invoiceId
                    ),
                    updated_at = now()
                WHERE invoice_id = :invoiceId
                """;
        namedJdbcTemplate.update(sql, new MapSqlParameterSource("invoiceId", invoiceId));
    }

    public int issueInvoices(BillingPeriod period) {
        String sql = """
                UPDATE invoices
                SET status = 'ISSUED',
                    updated_at = now()
                WHERE period_start = :periodStart
                  AND period_end = :periodEnd
                """;
        return namedJdbcTemplate.update(sql, periodParameters(period));
    }

    public int corruptFirstInvoiceLine(BillingPeriod period, long deltaCents) {
        String sql = """
                UPDATE invoice_lines
                SET amount_cents = amount_cents + :deltaCents,
                    updated_at = now()
                WHERE line_id = (
                    SELECT line_id
                    FROM invoice_lines
                    WHERE period_start = :periodStart
                      AND period_end = :periodEnd
                    ORDER BY line_id
                    LIMIT 1
                )
                """;
        return namedJdbcTemplate.update(sql, periodParameters(period).addValue("deltaCents", deltaCents));
    }

    public Optional<InvoiceView> findInvoice(String invoiceId) {
        String invoiceSql = """
                SELECT invoice_id, customer_id, total_cents
                FROM invoices
                WHERE invoice_id = ?
                """;

        return jdbcTemplate.query(invoiceSql, (rs, rowNum) -> new InvoiceHeader(
                        rs.getString("invoice_id"),
                        rs.getString("customer_id"),
                        rs.getLong("total_cents")),
                        invoiceId)
                .stream()
                .findFirst()
                .map(header -> new InvoiceView(
                        header.invoiceId(),
                        header.customerId(),
                        header.totalCents(),
                        invoiceLines(invoiceId)));
    }

    public List<InvoiceView> invoicesFor(BillingPeriod period) {
        String sql = """
                SELECT invoice_id
                FROM invoices
                WHERE period_start = ?
                  AND period_end = ?
                ORDER BY customer_id
                """;
        return jdbcTemplate.queryForList(
                        sql,
                        String.class,
                        Timestamp.from(period.startInclusive()),
                        Timestamp.from(period.endExclusive()))
                .stream()
                .map(this::findInvoice)
                .flatMap(Optional::stream)
                .toList();
    }

    private List<InvoiceLineView> invoiceLines(String invoiceId) {
        String sql = """
                SELECT line_id,
                       invoice_id,
                       meter_id,
                       line_type,
                       quantity_units,
                       amount_cents,
                       source_aggregate_id,
                       adjustment_for_event_id
                FROM invoice_lines
                WHERE invoice_id = ?
                ORDER BY line_type, meter_id, line_id
                """;
        return jdbcTemplate.query(sql, invoiceLineMapper(), invoiceId);
    }

    private void upsertPeriod(BillingPeriod period, BillingPeriodStatus status, Instant closedAt) {
        String sql = """
                INSERT INTO billing_periods (
                    period_id,
                    period_start,
                    period_end,
                    status,
                    closed_at
                )
                VALUES (
                    :periodId,
                    :periodStart,
                    :periodEnd,
                    :status,
                    :closedAt
                )
                ON CONFLICT (period_start, period_end)
                DO UPDATE SET
                    status = EXCLUDED.status,
                    closed_at = COALESCE(EXCLUDED.closed_at, billing_periods.closed_at),
                    updated_at = now()
                """;

        namedJdbcTemplate.update(sql, periodParameters(period)
                .addValue("periodId", StableBillingIds.periodId(period.startInclusive(), period.endExclusive()))
                .addValue("status", status.name())
                .addValue("closedAt", closedAt == null ? null : Timestamp.from(closedAt)));
    }

    private static MapSqlParameterSource periodParameters(BillingPeriod period) {
        return new MapSqlParameterSource()
                .addValue("periodStart", Timestamp.from(period.startInclusive()))
                .addValue("periodEnd", Timestamp.from(period.endExclusive()));
    }

    private static RowMapper<UsageAggregate> usageAggregateMapper() {
        return (rs, rowNum) -> new UsageAggregate(
                rs.getString("aggregate_id"),
                rs.getString("customer_id"),
                rs.getString("meter_id"),
                rs.getTimestamp("period_start").toInstant(),
                rs.getTimestamp("period_end").toInstant(),
                rs.getLong("quantity_units"),
                rs.getLong("source_event_count"));
    }

    private static RowMapper<LateUsageEvent> lateEventMapper() {
        return (rs, rowNum) -> new LateUsageEvent(
                rs.getString("event_id"),
                rs.getString("customer_id"),
                rs.getString("meter_id"),
                rs.getLong("quantity_units"),
                rs.getTimestamp("event_timestamp").toInstant(),
                rs.getTimestamp("original_period_start").toInstant(),
                rs.getTimestamp("original_period_end").toInstant());
    }

    private static RowMapper<InvoiceLineView> invoiceLineMapper() {
        return new RowMapper<>() {
            @Override
            public InvoiceLineView mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new InvoiceLineView(
                        rs.getString("line_id"),
                        rs.getString("invoice_id"),
                        rs.getString("meter_id"),
                        InvoiceLineType.valueOf(rs.getString("line_type")),
                        rs.getLong("quantity_units"),
                        rs.getLong("amount_cents"),
                        rs.getString("source_aggregate_id"),
                        rs.getString("adjustment_for_event_id"));
            }
        };
    }

    private record InvoiceHeader(String invoiceId, String customerId, long totalCents) {
    }
}
