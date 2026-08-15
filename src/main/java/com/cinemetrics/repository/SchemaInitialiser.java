package com.cinemetrics.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs once on startup — creates ClickHouse tables if they don't exist.
 * Safe to re-run (CREATE TABLE IF NOT EXISTS).
 */
@Component
public class SchemaInitialiser implements ApplicationRunner {
    @org.springframework.beans.factory.annotation.Autowired
    public SchemaInitialiser(ClickHouseQueryEngine queryEngine) {
        this.queryEngine = queryEngine;
    }

    private static final Logger log = LoggerFactory.getLogger(SchemaInitialiser.class);


    private final ClickHouseQueryEngine queryEngine;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Initialising ClickHouse schema...");
        createBoxOfficeDailyTable();
        createSentimentHourlyTable();
        createStreamingPerformanceTable();
        createMarketContextTable();
        log.info("ClickHouse schema ready.");
    }

    private void createBoxOfficeDailyTable() {
        queryEngine.executeUpdate("""
            CREATE TABLE IF NOT EXISTS box_office_daily (
                film_id        String,
                date           Date,
                region         String,
                gross_usd      UInt64,
                theatre_count  UInt32,
                week_number    UInt8
            ) ENGINE = MergeTree()
            PARTITION BY toYYYYMM(date)
            ORDER BY (film_id, date, region)
            """);
        log.info("Table box_office_daily ready");
    }

    private void createSentimentHourlyTable() {
        queryEngine.executeUpdate("""
            CREATE TABLE IF NOT EXISTS sentiment_hourly (
                film_id         String,
                timestamp       DateTime,
                source          String,
                sentiment_score Float32,
                mention_count   UInt32,
                sample_text     String
            ) ENGINE = MergeTree()
            PARTITION BY toYYYYMM(timestamp)
            ORDER BY (film_id, timestamp, source)
            """);
        log.info("Table sentiment_hourly ready");
    }

    private void createStreamingPerformanceTable() {
        queryEngine.executeUpdate("""
            CREATE TABLE IF NOT EXISTS streaming_performance (
                film_id              String,
                platform             String,
                date                 Date,
                views                UInt64,
                completion_rate      Float32,
                avg_watch_time_min   Float32
            ) ENGINE = MergeTree()
            PARTITION BY toYYYYMM(date)
            ORDER BY (film_id, date, platform)
            """);
        log.info("Table streaming_performance ready");
    }

    private void createMarketContextTable() {
        queryEngine.executeUpdate("""
            CREATE TABLE IF NOT EXISTS market_context (
                film_id       String,
                title         String,
                budget_usd    UInt64,
                release_date  Date,
                mpaa_rating   String,
                genre         String,
                director      String
            ) ENGINE = MergeTree()
            ORDER BY film_id
            """);
        log.info("Table market_context ready");
    }
}
