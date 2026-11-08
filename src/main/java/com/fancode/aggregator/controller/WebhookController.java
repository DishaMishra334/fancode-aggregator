package com.fancode.aggregator.controller;

import com.fancode.aggregator.dto.PushEventRequest;
import com.fancode.aggregator.dto.PushEventResponse;
import com.fancode.aggregator.service.AggregatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
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
    public ResponseEntity<PushEventResponse> handleProviderAWebhook(@RequestBody PushEventRequest request) {
        PushEventResponse response = aggregatorService.processPushEvent(request);
        return ResponseEntity.ok(response);
    }
}
