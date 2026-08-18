package com.cinemetrics.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cinemetrics.model.AgentResponse;
import com.cinemetrics.repository.ClickHouseQueryEngine;
import com.cinemetrics.service.FilmContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.*;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.ChatSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * Core Gemini agent service.
 *
 * Implements the tool-call loop:
 *   1. Send user query + tool definitions to Gemini
 *   2. If Gemini calls a tool, dispatch it and feed result back
 *   3. Repeat until Gemini returns a text answer
 *   4. Parse and return structured AgentResponse
 */
@Service
public class GeminiAgentService {
    private static final Logger log = LoggerFactory.getLogger(GeminiAgentService.class);


    private final ClickHouseQueryEngine queryEngine;
    private final FilmContextService filmContextService;
    private final ObjectMapper objectMapper;

    @Value("${gemini.project-id}")
    private String projectId;

    @Value("${gemini.location:us-central1}")
    private String location;

    @Value("${gemini.model:gemini-1.5-pro-002}")
    private String modelName;

    @Value("${gemini.max-output-tokens:2048}")
    private int maxOutputTokens;

    @Value("${gemini.temperature:0.2}")
    private float temperature;

    // Track queries executed per agent run (for transparency in response)
    private final ThreadLocal<List<String>> executedQueries = ThreadLocal.withInitial(ArrayList::new);

    public GeminiAgentService(ClickHouseQueryEngine queryEngine,
                               FilmContextService filmContextService,
                               ObjectMapper objectMapper) {
        this.queryEngine = queryEngine;
        this.filmContextService = filmContextService;
        this.objectMapper = objectMapper;
    }

    public AgentResponse query(String userQuestion, String[] filmIds) {
        long start = System.currentTimeMillis();
        executedQueries.set(new ArrayList<>());

        try (VertexAI vertexAI = new VertexAI(projectId, location)) {
            GenerativeModel model = buildModel(vertexAI);
            ChatSession chat = model.startChat();

            // Build context-aware system prompt
            String enrichedQuery = buildSystemPrompt(userQuestion, filmIds);
            log.info("Agent query: {}", userQuestion);

            GenerateContentResponse response = chat.sendMessage(enrichedQuery);

            // Tool-call loop — max 8 iterations to prevent infinite loops
            // Handles multiple function calls in a single Gemini turn correctly
            int iterations = 0;
            while (hasFunctionCall(response) && iterations < 8) {
                iterations++;

                // Extract ALL function calls from this turn (Gemini may batch multiple)
                List<FunctionCall> functionCalls = extractAllFunctionCalls(response);
                log.info("Agent calling {} tool(s) (iteration {})", functionCalls.size(), iterations);

                // Build one Content with ALL function responses
                Content.Builder toolResponseBuilder = Content.newBuilder().setRole("function");

                for (FunctionCall fc : functionCalls) {
                    log.info("  Tool: {}", fc.getName());
                    String toolResult = dispatchTool(fc);
                    log.debug("  Result length: {}", toolResult.length());

                    com.google.cloud.vertexai.api.Part functionResponsePart =
                        com.google.cloud.vertexai.api.Part.newBuilder()
                            .setFunctionResponse(
                                FunctionResponse.newBuilder()
                                    .setName(fc.getName())
                                    .setResponse(mapToStruct(toolResult))
                                    .build()
                            ).build();
                    toolResponseBuilder.addParts(functionResponsePart);
                }

                response = chat.sendMessage(toolResponseBuilder.build());
            }

            String answer = extractText(response);
            return buildStructuredResponse(answer, start);

        } catch (IOException e) {
            log.error("Gemini API error: {}", e.getMessage(), e);
            return AgentResponse.error("Gemini API error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Agent error: {}", e.getMessage(), e);
            return AgentResponse.error("Agent error: " + e.getMessage());
        } finally {
            executedQueries.remove();
        }
    }

    // ── Model construction ───────────────────────────────────────────────────

    private GenerativeModel buildModel(VertexAI vertexAI) {
        GenerationConfig config = GenerationConfig.newBuilder()
                .setMaxOutputTokens(maxOutputTokens)
                .setTemperature(temperature)
                .build();

        return new GenerativeModel.Builder()
                .setModelName(modelName)
                .setVertexAi(vertexAI)
                .setGenerationConfig(config)
                .setTools(List.of(buildToolDefinitions()))
                .build();
    }

    private Tool buildToolDefinitions() {
        return Tool.newBuilder()
                .addFunctionDeclarations(queryClickhouseTool())
                .addFunctionDeclarations(getFilmContextTool())
                .addFunctionDeclarations(compareFilmsTool())
                .addFunctionDeclarations(generateBriefingTool())
                .build();
    }

    private FunctionDeclaration queryClickhouseTool() {
        return FunctionDeclaration.newBuilder()
                .setName("query_clickhouse")
                .setDescription("""
                    Execute a SELECT SQL query against the ClickHouse analytics database.
                    Use this to retrieve box office data, sentiment scores, streaming performance,
                    or any aggregated analytics. Always use proper ClickHouse SQL syntax.
                    Available tables: box_office_daily, sentiment_hourly, streaming_performance, market_context.
                    """)
                .setParameters(Schema.newBuilder()
                        .setType(Type.OBJECT)
                        .putProperties("sql", Schema.newBuilder()
                                .setType(Type.STRING)
                                .setDescription("A valid ClickHouse SELECT statement. Use only SELECT queries.")
                                .build())
                        .addRequired("sql")
                        .build())
                .build();
    }

    private FunctionDeclaration getFilmContextTool() {
        return FunctionDeclaration.newBuilder()
                .setName("get_film_context")
                .setDescription("""
                    Get metadata for a specific film: budget, release date, genre, director, MPAA rating,
                    and number of days in release. Use this to contextualise revenue figures.
                    """)
                .setParameters(Schema.newBuilder()
                        .setType(Type.OBJECT)
                        .putProperties("film_id", Schema.newBuilder()
                                .setType(Type.STRING)
                                .setDescription("The film ID, e.g. film_001")
                                .build())
                        .addRequired("film_id")
                        .build())
                .build();
    }

    private FunctionDeclaration compareFilmsTool() {
        return FunctionDeclaration.newBuilder()
                .setName("compare_films")
                .setDescription("""
                    Compare multiple films on a specific metric over a given time period.
                    Use this when the question involves comparison between two or more films.
                    Returns week-by-week comparison data.
                    """)
                .setParameters(Schema.newBuilder()
                        .setType(Type.OBJECT)
                        .putProperties("film_ids", Schema.newBuilder()
                                .setType(Type.STRING)
                                .setDescription("Comma-separated list of film IDs to compare")
                                .build())
                        .putProperties("metric", Schema.newBuilder()
                                .setType(Type.STRING)
                                .setDescription("Metric to compare: gross_usd | sentiment_score | theatre_count | views")
                                .build())
                        .addRequired("film_ids")
                        .addRequired("metric")
                        .build())
                .build();
    }

    private FunctionDeclaration generateBriefingTool() {
        return FunctionDeclaration.newBuilder()
                .setName("generate_briefing")
                .setDescription("""
                    Generate a comprehensive performance snapshot for a list of films.
                    Returns the latest week's gross, week-on-week change, and average sentiment
                    for each film. Use this for overview or morning briefing questions.
                    """)
                .setParameters(Schema.newBuilder()
                        .setType(Type.OBJECT)
                        .putProperties("film_ids", Schema.newBuilder()
                                .setType(Type.STRING)
                                .setDescription("Comma-separated list of film IDs")
                                .build())
                        .addRequired("film_ids")
                        .build())
                .build();
    }

    // ── Tool dispatch ────────────────────────────────────────────────────────

    private String dispatchTool(FunctionCall fc) {
        return switch (fc.getName()) {
            case "query_clickhouse"  -> handleQueryClickhouse(fc);
            case "get_film_context"  -> handleGetFilmContext(fc);
            case "compare_films"     -> handleCompareFilms(fc);
            case "generate_briefing" -> handleGenerateBriefing(fc);
            default -> "{\"error\": \"Unknown tool: " + fc.getName() + "\"}";
        };
    }

    private String handleQueryClickhouse(FunctionCall fc) {
        String sql = getArg(fc, "sql");
        if (sql == null || sql.isBlank()) return "{\"error\": \"sql parameter is required\"}";
        executedQueries.get().add(sql);
        return queryEngine.execute(sql);
    }

    private String handleGetFilmContext(FunctionCall fc) {
        String filmId = getArg(fc, "film_id");
        if (filmId == null) return "{\"error\": \"film_id is required\"}";
        return filmContextService.getFilmContext(filmId);
    }

    private String handleCompareFilms(FunctionCall fc) {
        String filmIdsCsv = getArg(fc, "film_ids");
        String metric = getArg(fc, "metric");
        if (filmIdsCsv == null) return "{\"error\": \"film_ids is required\"}";
        if (metric == null) metric = "gross_usd";

        String[] ids = filmIdsCsv.split(",");
        String idList = Arrays.stream(ids)
                .map(String::trim)
                .map(id -> "'" + id.replaceAll("[^a-zA-Z0-9_\\-]", "") + "'")
                .reduce((a, b) -> a + "," + b)
                .orElse("''");

        String sql = String.format("""
            SELECT
                film_id,
                week_number,
                SUM(gross_usd) AS gross_usd,
                AVG(theatre_count) AS theatre_count
            FROM box_office_daily
            WHERE film_id IN (%s)
            GROUP BY film_id, week_number
            ORDER BY film_id, week_number
            """, idList);
        executedQueries.get().add(sql);
        return queryEngine.execute(sql);
    }

    private String handleGenerateBriefing(FunctionCall fc) {
        String filmIdsCsv = getArg(fc, "film_ids");
        if (filmIdsCsv == null) return "{\"error\": \"film_ids is required\"}";

        String[] ids = filmIdsCsv.split(",");
        String idList = Arrays.stream(ids)
                .map(String::trim)
                .map(id -> "'" + id.replaceAll("[^a-zA-Z0-9_\\-]", "") + "'")
                .reduce((a, b) -> a + "," + b)
                .orElse("''");

        // Latest week gross + week-on-week change + sentiment
        String sql = String.format("""
            WITH latest AS (
                SELECT film_id, max(week_number) AS current_week
                FROM box_office_daily
                WHERE film_id IN (%s)
                GROUP BY film_id
            ),
            current_week_data AS (
                SELECT b.film_id, SUM(b.gross_usd) AS current_gross, b.week_number
                FROM box_office_daily b
                JOIN latest l ON b.film_id = l.film_id AND b.week_number = l.current_week
                GROUP BY b.film_id, b.week_number
            ),
            prev_week_data AS (
                SELECT b.film_id, SUM(b.gross_usd) AS prev_gross
                FROM box_office_daily b
                JOIN latest l ON b.film_id = l.film_id AND b.week_number = l.current_week - 1
                GROUP BY b.film_id
            )
            SELECT
                c.film_id,
                c.week_number,
                c.current_gross,
                p.prev_gross,
                round((c.current_gross - p.prev_gross) / p.prev_gross * 100, 1) AS wow_change_pct
            FROM current_week_data c
            LEFT JOIN prev_week_data p ON c.film_id = p.film_id
            ORDER BY c.current_gross DESC
            """, idList);
        executedQueries.get().add(sql);
        return queryEngine.execute(sql);
    }

    // ── Response parsing ─────────────────────────────────────────────────────

    private AgentResponse buildStructuredResponse(String rawAnswer, long startTime) {
        AgentResponse.Builder builder = AgentResponse.builder()
                .answer(rawAnswer)
                .queriesExecuted(new ArrayList<>(executedQueries.get()))
                .processingMs(System.currentTimeMillis() - startTime)
                .timestamp(Instant.now())
                .error(false);

        // Try to extract structured recommendation from the answer
        String upper = rawAnswer.toUpperCase();
        if (upper.contains("EXTEND"))       builder.recommendation("EXTEND");
        else if (upper.contains("PULL"))    builder.recommendation("PULL");
        else if (upper.contains("HOLD"))    builder.recommendation("HOLD");
        else if (upper.contains("MONITOR")) builder.recommendation("MONITOR");

        // Extract confidence if Gemini mentions it
        if (upper.contains("HIGH CONFIDENCE"))        builder.confidence(0.85);
        else if (upper.contains("MODERATE CONFIDENCE")) builder.confidence(0.65);
        else if (upper.contains("LOW CONFIDENCE"))    builder.confidence(0.45);
        else builder.confidence(0.70);

        // Extract risk factors (lines starting with "Risk:" or "⚠")
        List<String> risks = new ArrayList<>();
        for (String line : rawAnswer.split("\n")) {
            if (line.toLowerCase().startsWith("risk") || line.contains("⚠")) {
                risks.add(line.trim());
            }
        }
        builder.riskFactors(risks);

        return builder.build();
    }

    // ── Utility helpers ──────────────────────────────────────────────────────

    private String buildSystemPrompt(String question, String[] filmIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            You are CineMetrics, an intelligent box office analyst for a film studio.
            You have access to real-time data including box office receipts, social sentiment,
            and streaming performance across 10 active film releases.

            When answering questions:
            1. Always query the database first to get current data — never guess numbers.
            2. Contextualise revenue using the film's budget (a $10M week means different things
               for a $5M indie vs a $200M blockbuster).
            3. Provide a clear recommendation: EXTEND (keep in theatres), PULL (remove from theatres),
               HOLD (maintain current strategy), or MONITOR (watch closely before deciding).
            4. State your confidence level: High Confidence, Moderate Confidence, or Low Confidence.
            5. List any risk factors on their own lines starting with "Risk:".

            FILM CATALOGUE — always resolve film names to IDs before querying:
            film_001 = "Galactic Frontier"  (Sci-Fi,    budget $180M, released 2024-06-14)
            film_002 = "The Last Accord"    (Drama,     budget $45M,  released 2024-06-21)
            film_003 = "Speed Protocol"     (Action,    budget $95M,  released 2024-06-28)
            film_004 = "Midnight Sonata"    (Romance,   budget $8M,   released 2024-07-05)
            film_005 = "Iron Colossus 4"    (Action,    budget $250M, released 2024-07-12)
            film_006 = "The Quiet Storm"    (Thriller,  budget $22M,  released 2024-07-19)
            film_007 = "Neon Dynasty"       (Sci-Fi,    budget $78M,  released 2024-07-26)
            film_008 = "A Family Reborn"    (Family,    budget $15M,  released 2024-08-02)
            film_009 = "Fracture Point"     (Thriller,  budget $55M,  released 2024-08-09)
            film_010 = "Legends of the Deep"(Adventure, budget $130M, released 2024-08-16)
            
            When a user mentions a film by name, automatically look up its film_id
            and use that in your database queries. Never ask the user for the film_id.
            """);

        if (filmIds != null && filmIds.length > 0) {
            sb.append("\nFocus on these films: ").append(String.join(", ", filmIds)).append("\n");
        }

        sb.append("\nQuestion: ").append(question);
        return sb.toString();
    }

    private boolean hasFunctionCall(GenerateContentResponse response) {
        return response.getCandidatesList().stream()
                .flatMap(c -> c.getContent().getPartsList().stream())
                .anyMatch(Part::hasFunctionCall);
    }

    private List<FunctionCall> extractAllFunctionCalls(GenerateContentResponse response) {
        List<FunctionCall> calls = new ArrayList<>();
        for (Content c : response.getCandidatesList().get(0).getContent().getPartsList()
                .stream()
                .filter(p -> p.hasFunctionCall())
                .map(p -> response.getCandidates(0).getContent())
                .toList()) {
            // Collect all parts with function calls
            break; // content already captured below
        }
        // Simpler: iterate parts directly
        calls.clear();
        response.getCandidates(0).getContent().getPartsList().stream()
                .filter(p -> p.hasFunctionCall())
                .map(p -> p.getFunctionCall())
                .forEach(calls::add);
        return calls;
    }

    private FunctionCall extractFunctionCall(GenerateContentResponse response) {
        return response.getCandidatesList().stream()
                .flatMap(c -> c.getContent().getPartsList().stream())
                .filter(Part::hasFunctionCall)
                .map(Part::getFunctionCall)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No function call found"));
    }

    private String extractText(GenerateContentResponse response) {
        return response.getCandidatesList().stream()
                .flatMap(c -> c.getContent().getPartsList().stream())
                .filter(Part::hasText)
                .map(Part::getText)
                .reduce("", String::concat);
    }

    private String getArg(FunctionCall fc, String key) {
        com.google.protobuf.Struct args = fc.getArgs();
        com.google.protobuf.Value val = args.getFieldsMap().get(key);
        return val != null ? val.getStringValue() : null;
    }

    private com.google.protobuf.Struct mapToStruct(String json) {
        try {
            com.google.protobuf.Struct.Builder builder = com.google.protobuf.Struct.newBuilder();
            com.google.protobuf.util.JsonFormat.parser().merge(
                    "{\"result\": " + json + "}", builder);
            return builder.build();
        } catch (Exception e) {
            com.google.protobuf.Struct.Builder builder = com.google.protobuf.Struct.newBuilder();
            builder.putFields("result", com.google.protobuf.Value.newBuilder()
                    .setStringValue(json).build());
            return builder.build();
        }
    }
}
