package com.meterline.drilldown;

import com.meterline.events.JsonMetadataConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Repository
public class DrillDownRepository {
    private final JdbcTemplate jdbc;
    private final JsonMetadataConverter metadataConverter;

    public DrillDownRepository(JdbcTemplate jdbc, JsonMetadataConverter metadataConverter) {
        this.jdbc = jdbc;
        this.metadataConverter = metadataConverter;
    }

    public Optional<InvoiceDetail> invoice(String invoiceId) {
        List<InvoiceHeader> invoices = jdbc.query("""
                SELECT invoice_id, customer_id, period_start, period_end, status, total_cents, created_at
                FROM invoices WHERE invoice_id = ?
                """, (rs, row) -> new InvoiceHeader(
                rs.getString("invoice_id"), rs.getString("customer_id"),
                rs.getTimestamp("period_start").toInstant(), rs.getTimestamp("period_end").toInstant(),
                rs.getString("status"), rs.getLong("total_cents"), rs.getTimestamp("created_at").toInstant()), invoiceId);
        return invoices.stream().findFirst().map(header -> new InvoiceDetail(
                header.invoiceId(), header.customerId(), header.periodStart(), header.periodEnd(),
                header.status(), header.totalCents(), header.createdAt(), lines(header.invoiceId())));
    }

    public Optional<InvoiceLineDetail> line(String lineId) {
        return jdbc.query("""
                SELECT line_id, meter_id, line_type, quantity_units, amount_cents,
                       rate_millionths_of_cent, source_aggregate_id, adjustment_for_event_id,
                       period_start, period_end, description
                FROM invoice_lines WHERE line_id = ?
                """, (rs, row) -> line(rs), lineId).stream().findFirst();
    }

    public List<RawEventDetail> eventsFor(InvoiceLineDetail line) {
        String sql = line.adjustment()
                ? """
                  SELECT event_id, event_timestamp, quantity_units, event_type, source,
                         source_event_key, adjustment_for_event_id, metadata::text AS metadata
                  FROM raw_usage_events WHERE event_id = ?
                  """
                : """
                  SELECT event.event_id, event.event_timestamp, event.quantity_units, event.event_type,
                         event.source, event.source_event_key, event.adjustment_for_event_id,
                         event.metadata::text AS metadata
                  FROM raw_usage_events event
                  JOIN usage_aggregates usage_agg
                    ON usage_agg.aggregate_id = ?
                  WHERE event.customer_id = usage_agg.customer_id
                    AND event.meter_id = usage_agg.meter_id
                    AND event.event_timestamp >= usage_agg.period_start
                    AND event.event_timestamp < usage_agg.period_end
                  ORDER BY event.event_timestamp, event.event_id
                  """;
        Object[] parameters = line.adjustment()
                ? new Object[]{line.adjustmentForEventId()}
                : new Object[]{line.sourceAggregateId()};
        return jdbc.query(sql, (rs, row) -> new RawEventDetail(
                rs.getString("event_id"), rs.getTimestamp("event_timestamp").toInstant(),
                rs.getLong("quantity_units"), rs.getString("event_type"), rs.getString("source"),
                rs.getString("source_event_key"), rs.getString("adjustment_for_event_id"),
                metadataConverter.fromJson(rs.getString("metadata"))), parameters);
    }

    private List<InvoiceLineDetail> lines(String invoiceId) {
        return jdbc.query("""
                SELECT line_id, meter_id, line_type, quantity_units, amount_cents,
                       rate_millionths_of_cent, source_aggregate_id, adjustment_for_event_id,
                       period_start, period_end, description
                FROM invoice_lines WHERE invoice_id = ? ORDER BY line_type, meter_id, line_id
                """, (rs, row) -> line(rs), invoiceId);
    }

    private static InvoiceLineDetail line(ResultSet rs) throws SQLException {
        String type = rs.getString("line_type");
        java.time.Instant periodStart = rs.getTimestamp("period_start").toInstant();
        java.time.Instant periodEnd = rs.getTimestamp("period_end").toInstant();
        return new InvoiceLineDetail(rs.getString("line_id"), rs.getString("meter_id"), type,
                rs.getLong("quantity_units"), rs.getLong("amount_cents"),
                rs.getLong("rate_millionths_of_cent"), "volume", periodStart.toString(),
                "ADJUSTMENT".equals(type), rs.getString("source_aggregate_id"),
                rs.getString("adjustment_for_event_id"), periodStart, periodEnd, rs.getString("description"));
    }

    private record InvoiceHeader(
            String invoiceId,
            String customerId,
            java.time.Instant periodStart,
            java.time.Instant periodEnd,
            String status,
            long totalCents,
            java.time.Instant createdAt
    ) {
    }
}
