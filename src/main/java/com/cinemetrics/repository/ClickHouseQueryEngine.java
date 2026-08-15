package com.cinemetrics.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Low-level ClickHouse query executor.
 * All queries run as SELECT (read-only).
 * Returns results as a JSON string for Gemini tool responses.
 */
@Repository
public class ClickHouseQueryEngine {
    private static final Logger log = LoggerFactory.getLogger(ClickHouseQueryEngine.class);


    private final DataSource clickHouseDataSource;
    private final ObjectMapper objectMapper;

    public ClickHouseQueryEngine(DataSource clickHouseDataSource, ObjectMapper objectMapper) {
        this.clickHouseDataSource = clickHouseDataSource;
        this.objectMapper = objectMapper;
    }

    /**
     * Execute a SELECT query and return results as a JSON array string.
     * This is the method called by the Gemini agent tool: query_clickhouse(sql).
     */
    public String execute(String sql) {
        // Safety: only allow SELECT statements from the agent
        String trimmed = sql.trim().toUpperCase();
        if (!trimmed.startsWith("SELECT") && !trimmed.startsWith("WITH")) {
            throw new IllegalArgumentException("Only SELECT queries are permitted");
        }

        log.info("ClickHouse query: {}", sql);
        long start = System.currentTimeMillis();

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            String result = resultSetToJson(rs);
            log.info("ClickHouse query completed in {}ms, result length: {}",
                    System.currentTimeMillis() - start, result.length());
            return result;

        } catch (SQLException e) {
            log.error("ClickHouse query failed: {}", e.getMessage());
            return "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    /**
     * Execute a DDL or INSERT statement (not exposed to agent).
     * Used by ingestion pipeline and schema initialisation.
     */
    public void executeUpdate(String sql) {
        log.debug("ClickHouse update: {}", sql.substring(0, Math.min(200, sql.length())));
        try (Connection conn = clickHouseDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            log.error("ClickHouse update failed: {}", e.getMessage());
            throw new RuntimeException("ClickHouse update failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a batch INSERT for high-throughput ingestion.
     */
    public void executeBatch(String sql, List<Object[]> rows) {
        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (Object[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    stmt.setObject(i + 1, row[i]);
                }
                stmt.addBatch();
            }
            stmt.executeBatch();
            log.info("Batch insert of {} rows completed", rows.size());

        } catch (SQLException e) {
            log.error("Batch insert failed: {}", e.getMessage());
            throw new RuntimeException("Batch insert failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check if a table exists in ClickHouse.
     */
    public boolean tableExists(String database, String tableName) {
        String sql = String.format(
                "SELECT count() FROM system.tables WHERE database='%s' AND name='%s'",
                database, tableName);
        String result = execute(sql);
        return result.contains("\"count()\":1") || result.contains("\"count()\":\"1\"");
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private String resultSetToJson(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        ArrayNode array = objectMapper.createArrayNode();

        while (rs.next()) {
            ObjectNode row = objectMapper.createObjectNode();
            for (int i = 1; i <= cols; i++) {
                String col = meta.getColumnName(i);
                int type = meta.getColumnType(i);
                switch (type) {
                    case Types.BIGINT, Types.INTEGER, Types.SMALLINT ->
                            row.put(col, rs.getLong(i));
                    case Types.FLOAT, Types.DOUBLE, Types.REAL, Types.NUMERIC, Types.DECIMAL ->
                            row.put(col, rs.getDouble(i));
                    case Types.BOOLEAN, Types.BIT ->
                            row.put(col, rs.getBoolean(i));
                    case Types.DATE ->
                            row.put(col, rs.getDate(i) != null ? rs.getDate(i).toString() : null);
                    case Types.TIMESTAMP ->
                            row.put(col, rs.getTimestamp(i) != null ? rs.getTimestamp(i).toString() : null);
                    default ->
                            row.put(col, rs.getString(i));
                }
            }
            array.add(row);
        }

        try {
            return objectMapper.writeValueAsString(array);
        } catch (Exception e) {
            return "[]";
        }
    }
}
