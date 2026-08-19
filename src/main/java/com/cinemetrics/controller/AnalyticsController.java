package com.cinemetrics.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cinemetrics.model.StudioBriefing;
import com.cinemetrics.repository.ClickHouseQueryEngine;
import com.cinemetrics.service.BriefingService;
import com.cinemetrics.service.FilmContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    @org.springframework.beans.factory.annotation.Autowired
    public AnalyticsController(BriefingService briefingService, FilmContextService filmContextService,
                               ClickHouseQueryEngine queryEngine) {
        this.briefingService = briefingService;
        this.filmContextService = filmContextService;
        this.queryEngine = queryEngine;
    }

    private final ClickHouseQueryEngine queryEngine;
    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);


    private final BriefingService briefingService;
    private final FilmContextService filmContextService;

    /**
     * GET /api/analytics/briefing
     * Returns the daily AI-generated studio briefing (cached, regenerated at 6am UTC).
     * This is what the Studio Briefing UI in Node.js polls.
     */
    @GetMapping("/briefing")
    public ResponseEntity<StudioBriefing> getBriefing() {
        StudioBriefing briefing = briefingService.getBriefing();
        return briefing != null
                ? ResponseEntity.ok(briefing)
                : ResponseEntity.noContent().build();
    }

    /**
     * GET /api/analytics/films
     * Returns all films in the database (for the film selector dropdown in the UI).
     */
    @GetMapping("/films")
    public ResponseEntity<String> listFilms() {
        return ResponseEntity.ok(filmContextService.listAllFilms());
    }

    /**
     * GET /api/analytics/film/{filmId}
     * Returns full analytics for a specific film: weekly revenue + recent sentiment.
     * Used by the real-time charts in the Node.js dashboard.
     */
    @GetMapping("/film/{filmId}")
    public ResponseEntity<String> getFilmAnalytics(@PathVariable String filmId) {
        // Validate format before hitting DB
        if (!filmId.matches("film_\\d{3}")) {
            return ResponseEntity.badRequest().body("{\"error\": \"Invalid film ID format\"}");
        }
        String context   = filmContextService.getFilmContext(filmId);
        String weekly    = filmContextService.getWeeklyRevenueSummary(filmId);
        String sentiment = filmContextService.getRecentSentiment(filmId, 30);

        String combined = String.format("""
            {
              "context": %s,
              "weekly_revenue": %s,
              "recent_sentiment": %s
            }
            """, context, weekly, sentiment);

        return ResponseEntity.ok(combined);
    }

    /**
     * POST /api/analytics/briefing/refresh
     * Force-regenerates the briefing (useful after seeding new data).
     */
    @PostMapping("/briefing/refresh")
    public ResponseEntity<String> refreshBriefing() {
        log.info("Manual briefing refresh requested");
        briefingService.generateBriefing();
        return ResponseEntity.ok("{\"status\": \"briefing regenerated\"}");
    }
    @org.springframework.web.bind.annotation.GetMapping("/chart-data/{filmId}")
    public ResponseEntity<?> getChartData(@PathVariable String filmId) {
        try {
            String boxOfficeJson = queryEngine.execute(
                "SELECT week_number, SUM(gross_usd) AS gross, AVG(theatre_count) AS theatres " +
                "FROM cinemetrics.box_office_daily " +
                "WHERE film_id = '" + filmId + "' " +
                "GROUP BY week_number ORDER BY week_number LIMIT 13"
            );
            String sentimentJson = queryEngine.execute(
                "SELECT toDate(timestamp) AS day, " +
                "AVG(sentiment_score) AS sentiment, SUM(mention_count) AS mentions " +
                "FROM cinemetrics.sentiment_hourly " +
                "WHERE film_id = '" + filmId + "' " +
                "GROUP BY day ORDER BY day LIMIT 30"
            );
            String streamingJson = queryEngine.execute(
                "SELECT platform, SUM(views) AS views, AVG(completion_rate) AS completion " +
                "FROM cinemetrics.streaming_performance " +
                "WHERE film_id = '" + filmId + "' " +
                "GROUP BY platform ORDER BY views DESC"
            );
            String metaJson = queryEngine.execute(
                "SELECT film_id, title, genre, budget_usd, release_date " +
                "FROM cinemetrics.market_context " +
                "WHERE film_id = '" + filmId + "'"
            );
            ObjectMapper mapper = new ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode result = mapper.createObjectNode();
            result.set("box_office", mapper.readTree(boxOfficeJson));
            result.set("sentiment", mapper.readTree(sentimentJson));
            result.set("streaming", mapper.readTree(streamingJson));
            result.set("meta", mapper.readTree(metaJson));
            result.put("film_id", filmId);
            result.put("source", "clickhouse_live");
            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            log.error("Chart data failed for {}: {}", filmId, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(java.util.Map.of("error", e.getMessage()));
        }
    }
}