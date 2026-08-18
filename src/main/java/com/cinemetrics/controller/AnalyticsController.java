package com.cinemetrics.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cinemetrics.model.StudioBriefing;
import com.cinemetrics.service.BriefingService;
import com.cinemetrics.service.FilmContextService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    @org.springframework.beans.factory.annotation.Autowired
    public AnalyticsController(BriefingService briefingService, FilmContextService filmContextService) {
        this.briefingService = briefingService;
        this.filmContextService = filmContextService;
    }

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
}