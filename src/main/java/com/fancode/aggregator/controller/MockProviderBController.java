package com.fancode.aggregator.controller;

import com.fancode.aggregator.dto.PollCommentaryResponse;
import com.fancode.aggregator.dto.PollScoreResponse;
import com.fancode.aggregator.service.MockProviderBService;
import com.fancode.aggregator.service.ProviderBPollerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Tag(name = "Mock Provider B (Poll Simulation)", description = "Simulation endpoints for Provider B polling feed and test controls")
public class MockProviderBController {

    private final MockProviderBService mockProviderBService;
    private final ProviderBPollerService pollerService;

    public MockProviderBController(MockProviderBService mockProviderBService,
                                   ProviderBPollerService pollerService) {
        this.mockProviderBService = mockProviderBService;
        this.pollerService = pollerService;
    }

    /**
     * GET /provider/matches/:id/score
     * Returns score snapshot from Provider B.
     */
    @GetMapping("/provider/matches/{id}/score")
    @Operation(summary = "Get Provider B score snapshot", description = "Endpoint polled by aggregator to fetch Provider B current score snapshot.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Score snapshot returned"),
            @ApiResponse(responseCode = "503", description = "Simulated temporary outage")
    })
    public ResponseEntity<?> getProviderBScore(
            @Parameter(description = "Provider match identifier", example = "b-match-1001")
            @PathVariable("id") String matchId) {
        try {
            PollScoreResponse response = mockProviderBService.getScore(matchId);
            return ResponseEntity.ok(response);
        } catch (MockProviderBService.MockProviderUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", ex.getMessage(), "status", 503));
        }
    }

    /**
     * GET /provider/matches/:id/commentary
     * Returns commentary items from Provider B.
     */
    @GetMapping("/provider/matches/{id}/commentary")
    @Operation(summary = "Get Provider B commentary items", description = "Endpoint polled by aggregator to fetch Provider B commentary items.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Commentary items returned"),
            @ApiResponse(responseCode = "503", description = "Simulated temporary outage")
    })
    public ResponseEntity<?> getProviderBCommentary(
            @Parameter(description = "Provider match identifier", example = "b-match-1001")
            @PathVariable("id") String matchId) {
        try {
            PollCommentaryResponse response = mockProviderBService.getCommentary(matchId);
            return ResponseEntity.ok(response);
        } catch (MockProviderBService.MockProviderUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", ex.getMessage(), "status", 503));
        }
    }

    /**
     * POST /mock/provider-b/scenario/{name}
     * Sets mock scenario: initial, unchanged, newer_score, new_ball, stale, error_503
     */
    @PostMapping("/mock/provider-b/scenario/{name}")
    @Operation(summary = "Switch Provider B mock scenario", description = "Selects one of: initial, unchanged, newer_score, new_ball, stale, error_503.")
    @ApiResponse(responseCode = "200", description = "Scenario updated successfully")
    public ResponseEntity<Map<String, String>> setScenario(
            @Parameter(description = "Scenario name (initial, unchanged, newer_score, new_ball, stale, error_503)", example = "newer_score")
            @PathVariable("name") String scenarioName) {
        mockProviderBService.setScenario(scenarioName);
        return ResponseEntity.ok(Map.of("status", "scenario_updated", "currentScenario", scenarioName));
    }

    /**
     * POST /mock/provider-b/poll
     * Triggers an on-demand poll cycle.
     */
    @PostMapping("/mock/provider-b/poll")
    @Operation(summary = "Trigger on-demand poll of Provider B", description = "Executes an immediate poll cycle against Provider B endpoints.")
    @ApiResponse(responseCode = "200", description = "Poll executed")
    public ResponseEntity<Map<String, Object>> triggerPoll(
            @Parameter(description = "Provider match identifier", example = "b-match-1001")
            @RequestParam(value = "matchId", defaultValue = "b-match-1001") String matchId) {
        boolean success = pollerService.pollOnce(matchId);
        return ResponseEntity.ok(Map.of("success", success, "matchId", matchId));
    }
}
