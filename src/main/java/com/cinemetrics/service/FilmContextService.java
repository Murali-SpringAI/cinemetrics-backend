package com.cinemetrics.service;

import com.cinemetrics.repository.ClickHouseQueryEngine;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FilmContextService {
    @org.springframework.beans.factory.annotation.Autowired
    public FilmContextService(ClickHouseQueryEngine queryEngine) {
        this.queryEngine = queryEngine;
    }


    private final ClickHouseQueryEngine queryEngine;

    /**
     * Get full context for a single film.
     * Called by Gemini tool: get_film_context(film_id)
     */
    public String getFilmContext(String filmId) {
        String sql = String.format("""
            SELECT
                film_id, title, budget_usd,
                release_date, mpaa_rating, genre, director,
                dateDiff('day', release_date, today()) AS days_in_release
            FROM market_context
            WHERE film_id = '%s'
            LIMIT 1
            """, sanitise(filmId));
        return queryEngine.execute(sql);
    }

    /**
     * List all films in the database.
     * Used by the briefing service and analytics endpoints.
     */
    public String listAllFilms() {
        return queryEngine.execute("""
            SELECT film_id, title, genre, release_date, budget_usd
            FROM market_context
            ORDER BY release_date DESC
            """);
    }

    /**
     * Get weekly revenue summary for a film — used by analytics endpoint.
     */
    public String getWeeklyRevenueSummary(String filmId) {
        String sql = String.format("""
            SELECT
                week_number,
                SUM(gross_usd)      AS total_gross_usd,
                AVG(theatre_count)  AS avg_theatres,
                COUNT(DISTINCT date) AS days_tracked
            FROM box_office_daily
            WHERE film_id = '%s'
            GROUP BY week_number
            ORDER BY week_number
            """, sanitise(filmId));
        return queryEngine.execute(sql);
    }

    /**
     * Get recent sentiment trend for a film.
     */
    public String getRecentSentiment(String filmId, int days) {
        String sql = String.format("""
            SELECT
                toDate(timestamp)       AS day,
                AVG(sentiment_score)    AS avg_sentiment,
                SUM(mention_count)      AS total_mentions
            FROM sentiment_hourly
            WHERE film_id = '%s'
              AND timestamp >= now() - INTERVAL %d DAY
            GROUP BY day
            ORDER BY day
            """, sanitise(filmId), days);
        return queryEngine.execute(sql);
    }

    /**
     * Get all film IDs currently in the database.
     */
    public List<String> getAllFilmIds() {
        String result = queryEngine.execute("SELECT film_id FROM market_context ORDER BY release_date");
        // Simple parse — extract film_id values from JSON array
        List<String> ids = new java.util.ArrayList<>();
        for (String part : result.split("\"film_id\":\"")) {
            if (part.contains("\"")) {
                ids.add(part.substring(0, part.indexOf("\"")));
            }
        }
        ids.remove(0); // first split is before any film_id
        return ids;
    }

    // Prevent SQL injection in agent-generated queries
    private String sanitise(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9_\\-]", "");
    }
}
