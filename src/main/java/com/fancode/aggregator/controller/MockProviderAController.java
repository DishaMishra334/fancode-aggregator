package com.fancode.aggregator.controller;

import com.fancode.aggregator.dto.PushEventResponse;
import com.fancode.aggregator.service.ProviderAGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/mock/provider-a")
@Tag(name = "Mock Provider A (Push Simulation)", description = "Control and trigger endpoints to simulate Provider A push timeline")
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
    @Operation(summary = "Play the 6-step worked scenario", description = "Executes the exact 6-step push sequence from the specification: ordered arrival, duplicate deliveryId, out-of-order gap arrival, late older snapshot, and revision 2 commentary correction.")
    @ApiResponse(responseCode = "200", description = "All 6 steps executed successfully")
    public ResponseEntity<List<PushEventResponse>> playWorkedScenario(
            @Parameter(description = "Provider match identifier (defaults to a-match-1001)", example = "a-match-1001")
            @RequestParam(value = "matchId", defaultValue = "a-match-1001") String matchId) {
        List<PushEventResponse> responses = generatorService.runWorkedPushScenario(matchId);
        return ResponseEntity.ok(responses);
    }
}
