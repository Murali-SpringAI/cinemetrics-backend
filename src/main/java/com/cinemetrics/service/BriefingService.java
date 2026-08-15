package com.cinemetrics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cinemetrics.agent.GeminiAgentService;
import com.cinemetrics.model.AgentResponse;
import com.cinemetrics.model.StudioBriefing;
import com.cinemetrics.repository.ClickHouseQueryEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generates the daily studio briefing — a snapshot of all active films
 * with AI-powered recommendations for each.
 *
 * The briefing is regenerated on a schedule and cached in memory.
 * The Node.js frontend polls GET /api/analytics/briefing.
 */
@Service
public class BriefingService {
    @org.springframework.beans.factory.annotation.Autowired
    public BriefingService(GeminiAgentService agentService, FilmContextService filmContextService, ClickHouseQueryEngine queryEngine, ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.filmContextService = filmContextService;
        this.queryEngine = queryEngine;
        this.objectMapper = objectMapper;
    }

    private static final Logger log = LoggerFactory.getLogger(BriefingService.class);


    private final GeminiAgentService agentService;
    private final FilmContextService filmContextService;
    private final ClickHouseQueryEngine queryEngine;
    private final ObjectMapper objectMapper;

    // In-memory cache — last generated briefing
    private final AtomicReference<StudioBriefing> cachedBriefing = new AtomicReference<>();

    /**
     * Returns the current briefing, generating one if none exists yet.
     */
    public StudioBriefing getBriefing() {
        StudioBriefing current = cachedBriefing.get();
        if (current == null) {
            log.info("No cached briefing — generating now...");
            generateBriefing();
        }
        return cachedBriefing.get();
    }

    /**
     * Scheduled: regenerate every day at 6am UTC.
     * Also called manually on first request.
     */
    @Scheduled(cron = "${briefing.schedule:0 0 6 * * *}")
    public void generateBriefing() {
        log.info("Generating daily studio briefing...");
        try {
            List<String> filmIds = filmContextService.getAllFilmIds();
            if (filmIds.isEmpty()) {
                log.warn("No films in database — briefing skipped");
                return;
            }

            List<StudioBriefing.FilmSnapshot> snapshots = new ArrayList<>();
            List<String> highlights = new ArrayList<>();
            List<String> riskAlerts = new ArrayList<>();

            for (String filmId : filmIds) {
                StudioBriefing.FilmSnapshot snapshot = buildFilmSnapshot(filmId);
                if (snapshot != null) {
                    snapshots.add(snapshot);
                    if ("PULL".equals(snapshot.getRecommendation())) {
                        riskAlerts.add(snapshot.getTitle() + " is underperforming — consider pulling from theatres");
                    }
                    if ("EXTEND".equals(snapshot.getRecommendation())) {
                        highlights.add(snapshot.getTitle() + " is outperforming — strong case for extended run");
                    }
                }
            }

            // Generate overall summary via agent
            String[] allIds = filmIds.toArray(new String[0]);
            AgentResponse summary = agentService.query(
                    "Provide a 2-sentence executive summary of all active film performance today. " +
                    "Highlight the biggest winner and biggest concern.", allIds);

            StudioBriefing briefing = StudioBriefing.builder()
                    .generatedDate(LocalDate.now())
                    .summary(summary.getAnswer())
                    .filmSnapshots(snapshots)
                    .topRecommendation(snapshots.isEmpty() ? "No active films" :
                            snapshots.get(0).getTitle() + " — " + snapshots.get(0).getRecommendation())
                    .marketHighlights(highlights)
                    .riskAlerts(riskAlerts)
                    .build();

            cachedBriefing.set(briefing);
            log.info("Daily briefing generated: {} films, {} highlights, {} risks",
                    snapshots.size(), highlights.size(), riskAlerts.size());

        } catch (Exception e) {
            log.error("Failed to generate briefing: {}", e.getMessage(), e);
        }
    }

    private StudioBriefing.FilmSnapshot buildFilmSnapshot(String filmId) {
        try {
            // Get film title
            String contextJson = filmContextService.getFilmContext(filmId);
            JsonNode context = objectMapper.readTree(contextJson);
            if (!context.isArray() || context.isEmpty()) return null;
            String title = context.get(0).path("title").asText("Unknown");
            Long budget = context.get(0).path("budget_usd").asLong(0);

            // Get latest week performance
            String weeklyJson = queryEngine.execute(String.format("""
                SELECT
                    week_number,
                    SUM(gross_usd) AS gross_usd
                FROM box_office_daily
                WHERE film_id = '%s'
                GROUP BY week_number
                ORDER BY week_number DESC
                LIMIT 2
                """, filmId));
            JsonNode weeks = objectMapper.readTree(weeklyJson);

            long currentGross = 0;
            long prevGross = 1;
            int weekNum = 1;
            if (weeks.isArray() && weeks.size() >= 1) {
                currentGross = weeks.get(0).path("gross_usd").asLong(0);
                weekNum = weeks.get(0).path("week_number").asInt(1);
                if (weeks.size() >= 2) prevGross = weeks.get(1).path("gross_usd").asLong(1);
            }
            double wowChange = prevGross > 0 ? (double)(currentGross - prevGross) / prevGross * 100 : 0;

            // Get recent sentiment
            String sentJson = queryEngine.execute(String.format("""
                SELECT AVG(sentiment_score) AS avg_sentiment
                FROM sentiment_hourly
                WHERE film_id = '%s'
                  AND timestamp >= now() - INTERVAL 7 DAY
                """, filmId));
            JsonNode sent = objectMapper.readTree(sentJson);
            double sentiment = 0.6;
            if (sent.isArray() && !sent.isEmpty()) {
                sentiment = sent.get(0).path("avg_sentiment").asDouble(0.6);
            }

            // Simple recommendation logic (Gemini adds nuance for detailed queries)
            String recommendation;
            double budgetRatio = budget > 0 ? (double) currentGross / (budget * 0.1) : 1.0;
            if (wowChange > 5 && sentiment > 0.7)       recommendation = "EXTEND";
            else if (wowChange < -40 || sentiment < 0.4) recommendation = "PULL";
            else if (wowChange < -20)                    recommendation = "MONITOR";
            else                                          recommendation = "HOLD";

            double confidence = Math.min(0.95, 0.5 + Math.abs(wowChange) / 100.0 + (sentiment - 0.5) * 0.3);

            return StudioBriefing.FilmSnapshot.builder()
                    .filmId(filmId)
                    .title(title)
                    .recommendation(recommendation)
                    .confidence(Math.round(confidence * 100.0) / 100.0)
                    .weekendGrossUsd(currentGross)
                    .weekOnWeekChangePct(Math.round(wowChange * 10.0) / 10.0)
                    .sentimentScore(Math.round(sentiment * 100.0) / 100.0)
                    .weekNumber(weekNum)
                    .rationale(String.format("W%d gross: $%,.0f | WoW: %.1f%% | Sentiment: %.2f",
                            weekNum, (double) currentGross, wowChange, sentiment))
                    .build();

        } catch (Exception e) {
            log.warn("Failed to build snapshot for {}: {}", filmId, e.getMessage());
            return null;
        }
    }
}
