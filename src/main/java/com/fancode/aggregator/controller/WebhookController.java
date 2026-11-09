package com.fancode.aggregator.controller;

import com.fancode.aggregator.dto.PushEventRequest;
import com.fancode.aggregator.dto.PushEventResponse;
import com.fancode.aggregator.service.AggregatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Push Ingestion", description = "Endpoints for external push providers to publish live match events")
public class WebhookController {

    private final AggregatorService aggregatorService;

    public WebhookController(AggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    /**
     * POST /api/webhooks/provider-a
     * Accepts webhook events from Provider A.
     * Enforces idempotency via deliveryId, snapshot monotonicity, and commentary revision rules.
     */
    @PostMapping("/provider-a")
    @Operation(summary = "Receive Provider A webhook event", description = "Ingests real-time events from Provider A with deliveryId idempotency, snapshot monotonicity, and commentary revision checks.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event processed (status: 'accepted' or 'duplicate_ignored')"),
            @ApiResponse(responseCode = "400", description = "Invalid payload (missing provider or deliveryId)")
    })
    public ResponseEntity<PushEventResponse> handleProviderAWebhook(@RequestBody PushEventRequest request) {
        PushEventResponse response = aggregatorService.processPushEvent(request);
        return ResponseEntity.ok(response);
    }
}
