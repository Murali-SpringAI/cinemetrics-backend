package com.cinemetrics.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

public class StudioBriefing {

    @JsonProperty("generated_date")
    private LocalDate generatedDate;
    private String summary;

    @JsonProperty("film_snapshots")
    private List<FilmSnapshot> filmSnapshots;

    @JsonProperty("top_recommendation")
    private String topRecommendation;

    @JsonProperty("market_highlights")
    private List<String> marketHighlights;

    @JsonProperty("risk_alerts")
    private List<String> riskAlerts;

    public StudioBriefing() {}

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private LocalDate generatedDate;
        private String summary;
        private List<FilmSnapshot> filmSnapshots;
        private String topRecommendation;
        private List<String> marketHighlights;
        private List<String> riskAlerts;

        public Builder generatedDate(LocalDate v)            { this.generatedDate = v; return this; }
        public Builder summary(String v)                     { this.summary = v; return this; }
        public Builder filmSnapshots(List<FilmSnapshot> v)   { this.filmSnapshots = v; return this; }
        public Builder topRecommendation(String v)           { this.topRecommendation = v; return this; }
        public Builder marketHighlights(List<String> v)      { this.marketHighlights = v; return this; }
        public Builder riskAlerts(List<String> v)            { this.riskAlerts = v; return this; }

        public StudioBriefing build() {
            StudioBriefing b = new StudioBriefing();
            b.generatedDate = generatedDate;
            b.summary = summary;
            b.filmSnapshots = filmSnapshots;
            b.topRecommendation = topRecommendation;
            b.marketHighlights = marketHighlights;
            b.riskAlerts = riskAlerts;
            return b;
        }
    }

    public LocalDate getGeneratedDate()             { return generatedDate; }
    public String getSummary()                      { return summary; }
    public List<FilmSnapshot> getFilmSnapshots()    { return filmSnapshots; }
    public String getTopRecommendation()            { return topRecommendation; }
    public List<String> getMarketHighlights()       { return marketHighlights; }
    public List<String> getRiskAlerts()             { return riskAlerts; }
    public void setGeneratedDate(LocalDate v)       { this.generatedDate = v; }
    public void setSummary(String v)                { this.summary = v; }
    public void setFilmSnapshots(List<FilmSnapshot> v) { this.filmSnapshots = v; }
    public void setTopRecommendation(String v)      { this.topRecommendation = v; }
    public void setMarketHighlights(List<String> v) { this.marketHighlights = v; }
    public void setRiskAlerts(List<String> v)       { this.riskAlerts = v; }

    public static class FilmSnapshot {
        @JsonProperty("film_id")
        private String filmId;
        private String title;
        private String recommendation;
        private Double confidence;

        @JsonProperty("weekend_gross_usd")
        private Long weekendGrossUsd;

        @JsonProperty("week_on_week_change_pct")
        private Double weekOnWeekChangePct;

        @JsonProperty("sentiment_score")
        private Double sentimentScore;

        @JsonProperty("week_number")
        private Integer weekNumber;

        private String rationale;

        public FilmSnapshot() {}

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String filmId, title, recommendation, rationale;
            private Double confidence, weekOnWeekChangePct, sentimentScore;
            private Long weekendGrossUsd;
            private Integer weekNumber;

            public Builder filmId(String v)                  { this.filmId = v; return this; }
            public Builder title(String v)                   { this.title = v; return this; }
            public Builder recommendation(String v)          { this.recommendation = v; return this; }
            public Builder confidence(Double v)              { this.confidence = v; return this; }
            public Builder weekendGrossUsd(Long v)           { this.weekendGrossUsd = v; return this; }
            public Builder weekOnWeekChangePct(Double v)     { this.weekOnWeekChangePct = v; return this; }
            public Builder sentimentScore(Double v)          { this.sentimentScore = v; return this; }
            public Builder weekNumber(Integer v)             { this.weekNumber = v; return this; }
            public Builder rationale(String v)               { this.rationale = v; return this; }

            public FilmSnapshot build() {
                FilmSnapshot s = new FilmSnapshot();
                s.filmId = filmId; s.title = title; s.recommendation = recommendation;
                s.confidence = confidence; s.weekendGrossUsd = weekendGrossUsd;
                s.weekOnWeekChangePct = weekOnWeekChangePct; s.sentimentScore = sentimentScore;
                s.weekNumber = weekNumber; s.rationale = rationale;
                return s;
            }
        }

        public String getFilmId()               { return filmId; }
        public String getTitle()                { return title; }
        public String getRecommendation()       { return recommendation; }
        public Double getConfidence()           { return confidence; }
        public Long getWeekendGrossUsd()        { return weekendGrossUsd; }
        public Double getWeekOnWeekChangePct()  { return weekOnWeekChangePct; }
        public Double getSentimentScore()       { return sentimentScore; }
        public Integer getWeekNumber()          { return weekNumber; }
        public String getRationale()            { return rationale; }
        public void setFilmId(String v)         { this.filmId = v; }
        public void setTitle(String v)          { this.title = v; }
        public void setRecommendation(String v) { this.recommendation = v; }
    }
}
