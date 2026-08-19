package com.cinemetrics.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cinemetrics.agent.GeminiAgentService;
import com.cinemetrics.model.AgentRequest;
import com.cinemetrics.model.AgentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @org.springframework.beans.factory.annotation.Autowired
    public AgentController(GeminiAgentService agentService) {
        this.agentService = agentService;
    }

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final GeminiAgentService agentService;

    @PostMapping("/query")
    public ResponseEntity<AgentResponse> query(@Valid @RequestBody AgentRequest request) {
        String query = request.getQuery();

        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(
                AgentResponse.error("Query cannot be empty")
            );
        }
        if (query.length() > 1000) {
            return ResponseEntity.badRequest().body(
                AgentResponse.error("Query too long — maximum 1000 characters")
            );
        }

        log.info("[AGENT] Query received ({} chars): {}", query.length(), query);
        long start = System.currentTimeMillis();

        AgentResponse response = agentService.query(query, request.getFilmIds());

        log.info("[AGENT] Query complete in {}ms — recommendation: {}",
            System.currentTimeMillis() - start, response.getRecommendation());

        return response.isError()
                ? ResponseEntity.internalServerError().body(response)
                : ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\": \"agent ready\"}");
    }
}
