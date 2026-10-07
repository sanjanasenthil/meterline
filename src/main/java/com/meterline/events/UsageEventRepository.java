package com.meterline.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class UsageEventRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final JsonMetadataConverter jsonMetadataConverter;

    public UsageEventRepository(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedJdbcTemplate,
            JsonMetadataConverter jsonMetadataConverter
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.jsonMetadataConverter = jsonMetadataConverter;
    }

    public boolean insertIfAbsent(String eventId, UsageEventRequest request) {
        String sql = """
                INSERT INTO raw_usage_events (
                    event_id,
                    customer_id,
                    meter_id,
                    source,
                    source_event_key,
                    quantity_units,
                    event_timestamp,
                    event_type,
                    adjustment_for_event_id,
                    metadata
                )
                VALUES (
                    :eventId,
                    :customerId,
                    :meterId,
                    :source,
                    :sourceEventKey,
                    :quantityUnits,
                    :eventTimestamp,
                    :eventType,
                    :adjustmentForEventId,
                    CAST(:metadata AS jsonb)
                )
                ON CONFLICT (event_id) DO NOTHING
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("customerId", request.customerId())
                .addValue("meterId", request.meterId())
                .addValue("source", request.source())
                .addValue("sourceEventKey", request.sourceEventKey())
                .addValue("quantityUnits", request.quantityUnits())
                .addValue("eventTimestamp", Timestamp.from(request.eventTimestamp()))
                .addValue("eventType", request.eventType().name())
                .addValue("adjustmentForEventId", blankToNull(request.adjustmentForEventId()))
                .addValue("metadata", jsonMetadataConverter.toJson(request.metadata()));

        return namedJdbcTemplate.update(sql, parameters) == 1;
    }

    public Optional<UsageEventRow> findById(String eventId) {
        String sql = """
                SELECT event_id,
                       customer_id,
                       meter_id,
                       source,
                       source_event_key,
                       quantity_units,
                       event_timestamp,
                       received_at,
                       event_type,
                       adjustment_for_event_id,
                       metadata::text AS metadata
                FROM raw_usage_events
                WHERE event_id = ?
                """;

        return jdbcTemplate.query(sql, rowMapper(), eventId).stream().findFirst();
    }

    public EventTotals totals(String customerId, String meterId, Instant startInclusive, Instant endExclusive) {
        String sql = """
                SELECT COUNT(*) AS event_count,
                       COALESCE(SUM(quantity_units), 0) AS quantity_units
                FROM raw_usage_events
                WHERE customer_id = ?
                  AND meter_id = ?
                  AND event_timestamp >= ?
                  AND event_timestamp < ?
                """;

        return jdbcTemplate.queryForObject(
                sql,
                (resultSet, rowNumber) -> new EventTotals(
                        resultSet.getLong("event_count"),
                        resultSet.getLong("quantity_units")),
                customerId,
                meterId,
                Timestamp.from(startInclusive),
                Timestamp.from(endExclusive));
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_usage_events", Long.class);
        return count == null ? 0 : count;
    }

    private RowMapper<UsageEventRow> rowMapper() {
        return new RowMapper<>() {
            @Override
            public UsageEventRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
                return new UsageEventRow(
                        resultSet.getString("event_id"),
                        resultSet.getString("customer_id"),
                        resultSet.getString("meter_id"),
                        resultSet.getString("source"),
                        resultSet.getString("source_event_key"),
                        resultSet.getLong("quantity_units"),
                        resultSet.getTimestamp("event_timestamp").toInstant(),
                        resultSet.getTimestamp("received_at").toInstant(),
                        EventType.valueOf(resultSet.getString("event_type")),
                        resultSet.getString("adjustment_for_event_id"),
                        jsonMetadataConverter.fromJson(resultSet.getString("metadata")));
            }
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
