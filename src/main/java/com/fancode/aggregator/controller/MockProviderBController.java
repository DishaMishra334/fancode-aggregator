package com.fancode.aggregator.controller;

import com.fancode.aggregator.dto.PollCommentaryResponse;
import com.fancode.aggregator.dto.PollScoreResponse;
import com.fancode.aggregator.service.MockProviderBService;
import com.fancode.aggregator.service.ProviderBPollerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
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
    public ResponseEntity<?> getProviderBScore(@PathVariable("id") String matchId) {
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
    public ResponseEntity<?> getProviderBCommentary(@PathVariable("id") String matchId) {
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
    public ResponseEntity<Map<String, String>> setScenario(@PathVariable("name") String scenarioName) {
        mockProviderBService.setScenario(scenarioName);
        return ResponseEntity.ok(Map.of("status", "scenario_updated", "currentScenario", scenarioName));
    }

    /**
     * POST /mock/provider-b/poll
     * Triggers an on-demand poll cycle.
     */
    @PostMapping("/mock/provider-b/poll")
    public ResponseEntity<Map<String, Object>> triggerPoll(@RequestParam(value = "matchId", defaultValue = "b-match-1001") String matchId) {
        boolean success = pollerService.pollOnce(matchId);
        return ResponseEntity.ok(Map.of("success", success, "matchId", matchId));
    }
}
