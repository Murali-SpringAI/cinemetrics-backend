package com.cinemetrics.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class AgentResponse {

    private String answer;
    private String recommendation;
    private Double confidence;

    @JsonProperty("supporting_data")
    private Map<String, Object> supportingData;

    @JsonProperty("risk_factors")
    private List<String> riskFactors;

    @JsonProperty("queries_executed")
    private List<String> queriesExecuted;

    @JsonProperty("processing_ms")
    private Long processingMs;

    private Instant timestamp;
    private boolean error;

    @JsonProperty("error_message")
    private String errorMessage;

    public AgentResponse() {}

    public AgentResponse(String answer, String recommendation, Double confidence,
                         Map<String, Object> supportingData, List<String> riskFactors,
                         List<String> queriesExecuted, Long processingMs,
                         Instant timestamp, boolean error, String errorMessage) {
        this.answer = answer;
        this.recommendation = recommendation;
        this.confidence = confidence;
        this.supportingData = supportingData;
        this.riskFactors = riskFactors;
        this.queriesExecuted = queriesExecuted;
        this.processingMs = processingMs;
        this.timestamp = timestamp;
        this.error = error;
        this.errorMessage = errorMessage;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String answer;
        private String recommendation;
        private Double confidence;
        private Map<String, Object> supportingData;
        private List<String> riskFactors;
        private List<String> queriesExecuted;
        private Long processingMs;
        private Instant timestamp;
        private boolean error;
        private String errorMessage;

        public Builder answer(String v)                          { this.answer = v; return this; }
        public Builder recommendation(String v)                  { this.recommendation = v; return this; }
        public Builder confidence(Double v)                      { this.confidence = v; return this; }
        public Builder supportingData(Map<String, Object> v)     { this.supportingData = v; return this; }
        public Builder riskFactors(List<String> v)               { this.riskFactors = v; return this; }
        public Builder queriesExecuted(List<String> v)           { this.queriesExecuted = v; return this; }
        public Builder processingMs(Long v)                      { this.processingMs = v; return this; }
        public Builder timestamp(Instant v)                      { this.timestamp = v; return this; }
        public Builder error(boolean v)                          { this.error = v; return this; }
        public Builder errorMessage(String v)                    { this.errorMessage = v; return this; }

        public AgentResponse build() {
            return new AgentResponse(answer, recommendation, confidence, supportingData,
                riskFactors, queriesExecuted, processingMs, timestamp, error, errorMessage);
        }
    }

    public static AgentResponse error(String message) {
        return AgentResponse.builder()
                .error(true)
                .errorMessage(message)
                .timestamp(Instant.now())
                .build();
    }

    public String getAnswer()                          { return answer; }
    public String getRecommendation()                  { return recommendation; }
    public Double getConfidence()                      { return confidence; }
    public Map<String, Object> getSupportingData()     { return supportingData; }
    public List<String> getRiskFactors()               { return riskFactors; }
    public List<String> getQueriesExecuted()           { return queriesExecuted; }
    public Long getProcessingMs()                      { return processingMs; }
    public Instant getTimestamp()                      { return timestamp; }
    public boolean isError()                           { return error; }
    public String getErrorMessage()                    { return errorMessage; }
    public void setAnswer(String v)                    { this.answer = v; }
    public void setRecommendation(String v)            { this.recommendation = v; }
    public void setConfidence(Double v)                { this.confidence = v; }
    public void setSupportingData(Map<String, Object> v) { this.supportingData = v; }
    public void setRiskFactors(List<String> v)         { this.riskFactors = v; }
    public void setQueriesExecuted(List<String> v)     { this.queriesExecuted = v; }
    public void setProcessingMs(Long v)                { this.processingMs = v; }
    public void setTimestamp(Instant v)                { this.timestamp = v; }
    public void setError(boolean v)                    { this.error = v; }
    public void setErrorMessage(String v)              { this.errorMessage = v; }
}
