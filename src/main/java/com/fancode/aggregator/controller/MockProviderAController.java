package com.fancode.aggregator.controller;

import com.fancode.aggregator.dto.PushEventResponse;
import com.fancode.aggregator.service.ProviderAGeneratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/mock/provider-a")
public class MockProviderAController {

    private final ProviderAGeneratorService generatorService;

    public MockProviderAController(ProviderAGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    /**
     * POST /mock/provider-a/play
     * Runs the 6-step worked scenario from the FanCode specification.
     */
    @PostMapping("/play")
    public ResponseEntity<List<PushEventResponse>> playWorkedScenario(
            @RequestParam(value = "matchId", defaultValue = "a-match-1001") String matchId) {
        List<PushEventResponse> responses = generatorService.runWorkedPushScenario(matchId);
        return ResponseEntity.ok(responses);
    }
}
