package com.fancode.aggregator.service;

import com.fancode.aggregator.dto.CommentaryDto;
import com.fancode.aggregator.dto.PushEventRequest;
import com.fancode.aggregator.dto.PushEventResponse;
import com.fancode.aggregator.dto.SnapshotDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProviderAGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(ProviderAGeneratorService.class);

    private final AggregatorService aggregatorService;

    public ProviderAGeneratorService(AggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    /**
     * Executes the exact 6-step scenario from the FanCode worked push example:
     * Step 1: d1, snapshot (1, 1) = 4/0 in 0.4, ball b1 rev 1, text "four"
     * Step 2: d2, snapshot (2, 1) = 4/1 in 0.5, ball b2 rev 1, text "out"
     * Step 3: d2 again (duplicate deliveryId)
     * Step 4: d4, snapshot (4, 1) = 10/1 in 1.1, ball b4 (out of order, sequence gap)
     * Step 5: d3, snapshot (3, 1) = 8/1 in 1.0, ball b3 (delayed older snapshot, late ball inserted)
     * Step 6: d5, snapshot (4, 1) repeated, ball b2 rev 2, text "out, review stays out" (commentary correction)
     */
    public List<PushEventResponse> runWorkedPushScenario(String providerMatchId) {
        if (providerMatchId == null) {
            providerMatchId = "a-match-1001";
        }
        List<PushEventResponse> results = new ArrayList<>();
        Instant baseTime = Instant.parse("2026-10-22T14:00:00.000Z");

        // Step 1: d1, snapshot (1, 1) = 4/0 in 0.4, ball b1 rev 1, text "four"
        PushEventRequest step1 = new PushEventRequest(
                "provider-a", "d1", baseTime, providerMatchId,
                new SnapshotDto(1, 1, "live", 1, 4, 0, "0.4", baseTime),
                new CommentaryDto("b1", 1, 1, 1, 0, 4, "four", baseTime)
        );
        results.add(aggregatorService.processPushEvent(step1));

        // Step 2: d2, snapshot (2, 1) = 4/1 in 0.5, ball b2 rev 1, text "out"
        PushEventRequest step2 = new PushEventRequest(
                "provider-a", "d2", baseTime.plusSeconds(30), providerMatchId,
                new SnapshotDto(2, 1, "live", 1, 4, 1, "0.5", baseTime.plusSeconds(30)),
                new CommentaryDto("b2", 2, 1, 1, 0, 5, "out", baseTime.plusSeconds(30))
        );
        results.add(aggregatorService.processPushEvent(step2));

        // Step 3: d2 again (repeated deliveryId) -> must be duplicate_ignored
        results.add(aggregatorService.processPushEvent(step2));

        // Step 4: d4, snapshot (4, 1) = 10/1 in 1.1, ball b4 rev 1, text "six"
        PushEventRequest step4 = new PushEventRequest(
                "provider-a", "d4", baseTime.plusSeconds(90), providerMatchId,
                new SnapshotDto(4, 1, "live", 1, 10, 1, "1.1", baseTime.plusSeconds(90)),
                new CommentaryDto("b4", 4, 1, 1, 1, 1, "six", baseTime.plusSeconds(90))
        );
        results.add(aggregatorService.processPushEvent(step4));

        // Step 5: d3, snapshot (3, 1) = 8/1 in 1.0, ball b3 rev 1, text "two runs" (delayed older snapshot)
        PushEventRequest step5 = new PushEventRequest(
                "provider-a", "d3", baseTime.plusSeconds(60), providerMatchId,
                new SnapshotDto(3, 1, "live", 1, 8, 1, "1.0", baseTime.plusSeconds(60)),
                new CommentaryDto("b3", 3, 1, 1, 1, 0, "two runs", baseTime.plusSeconds(60))
        );
        results.add(aggregatorService.processPushEvent(step5));

        // Step 6: d5, snapshot (4, 1) repeated, ball b2 rev 2, text "out, review stays out" (correction)
        PushEventRequest step6 = new PushEventRequest(
                "provider-a", "d5", baseTime.plusSeconds(120), providerMatchId,
                new SnapshotDto(4, 1, "live", 1, 10, 1, "1.1", baseTime.plusSeconds(120)),
                new CommentaryDto("b2", 2, 2, 1, 0, 5, "out, review stays out", baseTime.plusSeconds(120))
        );
        results.add(aggregatorService.processPushEvent(step6));

        log.info("Completed Provider A worked push scenario with 6 steps.");
        return results;
    }
}

