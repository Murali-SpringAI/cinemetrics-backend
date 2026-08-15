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

    /**
     * POST /api/agent/query
     *
     * Body: { "query": "Should we extend Film X's theatrical run?", "filmIds": ["film_001"] }
     * Returns: AgentResponse with recommendation, confidence, supporting data, queries executed
     *
     * This is the main endpoint your teammate's NL query UI will call.
     */
    @PostMapping("/query")
    public ResponseEntity<AgentResponse> query(@Valid @RequestBody AgentRequest request) {
        log.info("Agent query received: {}", request.getQuery());
        AgentResponse response = agentService.query(request.getQuery(), request.getFilmIds());
        return response.isError()
                ? ResponseEntity.internalServerError().body(response)
                : ResponseEntity.ok(response);
    }

    /**
     * GET /api/agent/health
     * Simple health check for the agent layer (does not call Gemini).
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\": \"agent ready\"}");
    }
}
