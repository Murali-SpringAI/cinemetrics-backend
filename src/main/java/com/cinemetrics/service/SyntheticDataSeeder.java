package com.cinemetrics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cinemetrics.repository.ClickHouseQueryEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds 10 synthetic films with 90 days of realistic box office,
 * sentiment, and streaming data. Runs after SchemaInitialiser (Order 2).
 *
 * Only seeds if SEED_ON_STARTUP=true (default for local dev and demo).
 * Safe to re-run — checks row count first.
 */
@Component
@Order(2)
public class SyntheticDataSeeder implements ApplicationRunner {
    @org.springframework.beans.factory.annotation.Autowired
    public SyntheticDataSeeder(ClickHouseQueryEngine queryEngine) {
        this.queryEngine = queryEngine;
    }

    private static final Logger log = LoggerFactory.getLogger(SyntheticDataSeeder.class);


    private final ClickHouseQueryEngine queryEngine;

    @Value("${ingestion.seed-on-startup:true}")
    private boolean seedOnStartup;

    private static final Random RNG = new Random(42); // fixed seed for reproducibility

    // 10 demo films with realistic data
    private static final Object[][] FILMS = {
        {"film_001", "Galactic Frontier",     180_000_000L, "2024-06-14", "PG-13", "Sci-Fi",   "Sarah Chen"},
        {"film_002", "The Last Accord",        45_000_000L, "2024-06-21", "R",     "Drama",    "Marcus Webb"},
        {"film_003", "Speed Protocol",         95_000_000L, "2024-06-28", "PG-13", "Action",   "Diana Torres"},
        {"film_004", "Midnight Sonata",         8_000_000L, "2024-07-05", "PG",    "Romance",  "James Holloway"},
        {"film_005", "Iron Colossus 4",       250_000_000L, "2024-07-12", "PG-13", "Action",   "Raj Patel"},
        {"film_006", "The Quiet Storm",        22_000_000L, "2024-07-19", "R",     "Thriller", "Amara Osei"},
        {"film_007", "Neon Dynasty",           78_000_000L, "2024-07-26", "R",     "Sci-Fi",   "Yuki Tanaka"},
        {"film_008", "A Family Reborn",        15_000_000L, "2024-08-02", "PG",    "Family",   "Carlos Mendes"},
        {"film_009", "Fracture Point",         55_000_000L, "2024-08-09", "R",     "Thriller", "Priya Sharma"},
        {"film_010", "Legends of the Deep",   130_000_000L, "2024-08-16", "PG-13", "Adventure","Finn O'Brien"},
    };

    @Override
    public void run(ApplicationArguments args) {
        if (!seedOnStartup) {
            log.info("Seed-on-startup disabled — skipping synthetic data");
            return;
        }

        // Check if already seeded
        String countResult = queryEngine.execute("SELECT count() as cnt FROM market_context");
        if (countResult.contains("\"cnt\":10") || countResult.contains("\"cnt\":\"10\"")) {
            log.info("Synthetic data already present — skipping seed");
            return;
        }

        log.info("Seeding synthetic data for {} films...", FILMS.length);
        seedMarketContext();
        seedBoxOfficeDaily();
        seedSentimentHourly();
        seedStreamingPerformance();
        log.info("Synthetic data seeding complete.");
    }

    private void seedMarketContext() {
        List<Object[]> rows = new ArrayList<>();
        for (Object[] f : FILMS) {
            rows.add(f);
        }
        queryEngine.executeBatch(
            "INSERT INTO market_context (film_id, title, budget_usd, release_date, mpaa_rating, genre, director) VALUES (?,?,?,?,?,?,?)",
            rows
        );
        log.info("Seeded {} market_context rows", rows.size());
    }

    private void seedBoxOfficeDaily() {
        List<Object[]> rows = new ArrayList<>();
        String[] regions = {"domestic", "uk", "australia", "germany", "japan"};

        for (Object[] film : FILMS) {
            String filmId = (String) film[0];
            Long budget = (Long) film[2];
            // Opening weekend target varies by budget
            long openingWeekend = (long) (budget * (0.4 + RNG.nextDouble() * 0.6));

            LocalDate releaseDate = LocalDate.parse((String) film[3]);

            for (int day = 0; day < 90; day++) {
                LocalDate date = releaseDate.plusDays(day);
                int weekNum = (day / 7) + 1;

                // Revenue decay curve: starts high, drops ~30-40% weekly
                double weekDecay = Math.pow(0.62 + RNG.nextDouble() * 0.15, weekNum - 1);
                // Weekend bump
                boolean isWeekend = date.getDayOfWeek().getValue() >= 6;
                double dayMultiplier = isWeekend ? 2.1 + RNG.nextDouble() * 0.6 : 0.5 + RNG.nextDouble() * 0.3;

                for (String region : regions) {
                    double regionMultiplier = switch (region) {
                        case "domestic" -> 1.0;
                        case "uk"       -> 0.18 + RNG.nextDouble() * 0.05;
                        case "australia"-> 0.08 + RNG.nextDouble() * 0.03;
                        case "germany"  -> 0.12 + RNG.nextDouble() * 0.04;
                        case "japan"    -> 0.15 + RNG.nextDouble() * 0.05;
                        default         -> 0.1;
                    };

                    long gross = Math.max(0, (long) (openingWeekend / 7.0 * dayMultiplier * weekDecay * regionMultiplier));
                    int theatres = (int) Math.max(50, (4200 * regionMultiplier * weekDecay) + RNG.nextInt(100));

                    rows.add(new Object[]{filmId, date.toString(), region, gross, theatres, weekNum});
                }
            }
        }

        // Insert in batches of 500
        for (int i = 0; i < rows.size(); i += 500) {
            List<Object[]> batch = rows.subList(i, Math.min(i + 500, rows.size()));
            queryEngine.executeBatch(
                "INSERT INTO box_office_daily (film_id, date, region, gross_usd, theatre_count, week_number) VALUES (?,?,?,?,?,?)",
                batch
            );
        }
        log.info("Seeded {} box_office_daily rows", rows.size());
    }

    private void seedSentimentHourly() {
        List<Object[]> rows = new ArrayList<>();
        String[] sources = {"twitter", "reddit", "letterboxd"};

        for (Object[] film : FILMS) {
            String filmId = (String) film[0];
            LocalDate releaseDate = LocalDate.parse((String) film[3]);

            // Base sentiment varies by film — some start hot, some cool
            double baseSentiment = 0.55 + RNG.nextDouble() * 0.3;

            for (int day = 0; day < 90; day++) {
                // Sentiment tends to stabilise downward over time
                double daySentiment = baseSentiment - (day * 0.001) + (RNG.nextGaussian() * 0.05);
                daySentiment = Math.max(0.1, Math.min(0.95, daySentiment));

                LocalDateTime baseTime = releaseDate.plusDays(day).atTime(8, 0);

                for (String source : sources) {
                    // 3 data points per source per day (morning, afternoon, evening)
                    for (int h : new int[]{0, 6, 14}) {
                        double sourceSentiment = daySentiment + (RNG.nextGaussian() * 0.03);
                        sourceSentiment = Math.max(0.1, Math.min(0.95, sourceSentiment));
                        int mentions = (int) (50 + RNG.nextInt(500) * Math.exp(-day * 0.02));

                        rows.add(new Object[]{
                            filmId,
                            baseTime.plusHours(h).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                            source,
                            (float) sourceSentiment,
                            mentions,
                            "Sample sentiment text for " + film[1]
                        });
                    }
                }
            }
        }

        for (int i = 0; i < rows.size(); i += 500) {
            List<Object[]> batch = rows.subList(i, Math.min(i + 500, rows.size()));
            queryEngine.executeBatch(
                "INSERT INTO sentiment_hourly (film_id, timestamp, source, sentiment_score, mention_count, sample_text) VALUES (?,?,?,?,?,?)",
                batch
            );
        }
        log.info("Seeded {} sentiment_hourly rows", rows.size());
    }

    private void seedStreamingPerformance() {
        List<Object[]> rows = new ArrayList<>();
        String[] platforms = {"netflix", "prime", "disney_plus", "apple_tv"};

        for (Object[] film : FILMS) {
            String filmId = (String) film[0];
            LocalDate releaseDate = LocalDate.parse((String) film[3]);
            // Streaming starts 45 days after theatrical release
            LocalDate streamStart = releaseDate.plusDays(45);

            for (int day = 0; day < 45; day++) {
                for (String platform : platforms) {
                    double platformShare = switch (platform) {
                        case "netflix"    -> 0.4 + RNG.nextDouble() * 0.1;
                        case "prime"      -> 0.25 + RNG.nextDouble() * 0.08;
                        case "disney_plus"-> 0.2 + RNG.nextDouble() * 0.08;
                        case "apple_tv"   -> 0.15 + RNG.nextDouble() * 0.05;
                        default           -> 0.1;
                    };

                    // Views decay over time on streaming too
                    long baseViews = 500_000 + (long) (RNG.nextDouble() * 2_000_000);
                    long views = (long) (baseViews * Math.pow(0.92, day) * platformShare);
                    float completionRate = (float) (0.55 + RNG.nextDouble() * 0.35);
                    float avgWatchTime = completionRate * 110; // ~110 min average film

                    rows.add(new Object[]{
                        filmId, platform,
                        streamStart.plusDays(day).toString(),
                        views, completionRate, avgWatchTime
                    });
                }
            }
        }

        for (int i = 0; i < rows.size(); i += 500) {
            List<Object[]> batch = rows.subList(i, Math.min(i + 500, rows.size()));
            queryEngine.executeBatch(
                "INSERT INTO streaming_performance (film_id, platform, date, views, completion_rate, avg_watch_time_min) VALUES (?,?,?,?,?,?)",
                batch
            );
        }
        log.info("Seeded {} streaming_performance rows", rows.size());
    }
}
