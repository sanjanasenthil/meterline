package com.meterline.reconciliation;

import com.meterline.pricing.BillingPeriod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class ReconciliationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReconciliationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RawUsageForReconciliation> rawUsageFor(BillingPeriod period) {
        String sql = """
                SELECT customer_id,
                       meter_id,
                       COALESCE(SUM(quantity_units), 0) AS quantity_units,
                       MIN(event_timestamp) AS first_event_at,
                       MAX(event_timestamp) AS last_event_at
                FROM raw_usage_events
                WHERE event_timestamp >= ?
                  AND event_timestamp < ?
                  AND event_type IN ('USAGE', 'ADJUSTMENT')
                GROUP BY customer_id, meter_id
                ORDER BY customer_id, meter_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new RawUsageForReconciliation(
                        rs.getString("customer_id"),
                        rs.getString("meter_id"),
                        rs.getLong("quantity_units"),
                        rs.getTimestamp("first_event_at").toInstant(),
                        rs.getTimestamp("last_event_at").toInstant()),
                Timestamp.from(period.startInclusive()),
                Timestamp.from(period.endExclusive()));
    }

    public List<InvoiceHeaderForReconciliation> issuedInvoicesFor(BillingPeriod period) {
        String sql = """
                SELECT invoice_id,
                       customer_id,
                       period_start,
                       period_end,
                       total_cents
                FROM invoices
                WHERE period_start = ?
                  AND period_end = ?
                  AND status = 'ISSUED'
                ORDER BY customer_id, invoice_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new InvoiceHeaderForReconciliation(
                        rs.getString("invoice_id"),
                        rs.getString("customer_id"),
                        rs.getTimestamp("period_start").toInstant(),
                        rs.getTimestamp("period_end").toInstant(),
                        rs.getLong("total_cents")),
                Timestamp.from(period.startInclusive()),
                Timestamp.from(period.endExclusive()));
    }

    public List<InvoiceLineForReconciliation> issuedInvoiceLinesFor(BillingPeriod period) {
        String sql = """
                SELECT i.invoice_id,
                       i.customer_id AS invoice_customer_id,
                       i.period_start AS invoice_period_start,
                       i.period_end AS invoice_period_end,
                       l.line_id,
                       l.customer_id AS line_customer_id,
                       l.meter_id,
                       l.period_start AS line_period_start,
                       l.period_end AS line_period_end,
                       l.amount_cents
                FROM invoices i
                JOIN invoice_lines l ON l.invoice_id = i.invoice_id
                WHERE i.period_start = ?
                  AND i.period_end = ?
                  AND i.status = 'ISSUED'
                ORDER BY i.customer_id, l.meter_id, l.line_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new InvoiceLineForReconciliation(
                        rs.getString("invoice_id"),
                        rs.getString("invoice_customer_id"),
                        rs.getString("line_id"),
                        rs.getString("line_customer_id"),
                        rs.getString("meter_id"),
                        rs.getTimestamp("invoice_period_start").toInstant(),
                        rs.getTimestamp("invoice_period_end").toInstant(),
                        rs.getTimestamp("line_period_start").toInstant(),
                        rs.getTimestamp("line_period_end").toInstant(),
                        rs.getLong("amount_cents")),
                Timestamp.from(period.startInclusive()),
                Timestamp.from(period.endExclusive()));
    }
}
