package com.fancode.aggregator.service;

import com.fancode.aggregator.dto.CommentaryDto;
import com.fancode.aggregator.dto.PollCommentaryResponse;
import com.fancode.aggregator.dto.PollScoreResponse;
import com.fancode.aggregator.dto.SnapshotDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class MockProviderBService {

    private static final Logger log = LoggerFactory.getLogger(MockProviderBService.class);

    private String scenario = "initial"; // initial, unchanged, newer_score, new_ball, stale, error_503
    private boolean simulate503 = false;

    public void setScenario(String scenario) {
        this.scenario = scenario;
        this.simulate503 = "error_503".equalsIgnoreCase(scenario);
        log.info("Mock Provider B scenario switched to: [{}]", scenario);
    }

    public String getScenario() {
        return scenario;
    }

    public PollScoreResponse getScore(String matchId) {
        if (simulate503) {
            throw new MockProviderUnavailableException("Provider B temporarily unavailable (503 Service Unavailable)");
        }

        SnapshotDto snapshot;
        Instant now = Instant.now();

        switch (scenario.toLowerCase()) {
            case "newer_score":
                // Newer score: sequence 16, revision 1, 104/2 in 13.0
                snapshot = new SnapshotDto(16, 1, "live", 1, 104, 2, "13.0", now);
                break;
            case "stale":
                // Stale response: sequence 10, revision 1 (older than sequence 15)
                snapshot = new SnapshotDto(10, 1, "live", 1, 80, 1, "10.0", now.minusSeconds(600));
                break;
            case "initial":
            case "unchanged":
            case "new_ball":
            default:
                // Standard snapshot: sequence 15, revision 1, 98/2 in 12.4
                snapshot = new SnapshotDto(15, 1, "live", 1, 98, 2, "12.4", now);
                break;
        }

        return new PollScoreResponse("provider-b", matchId, snapshot);
    }

    public PollCommentaryResponse getCommentary(String matchId) {
        if (simulate503) {
            throw new MockProviderUnavailableException("Provider B temporarily unavailable (503 Service Unavailable)");
        }

        List<CommentaryDto> items = new ArrayList<>();
        Instant baseTime = Instant.parse("2026-10-22T14:03:11.000Z");

        // Base ball 76
        items.add(new CommentaryDto(
                "inn1-12.4", 76, 1, 1, 12, 4,
                "Full and straight, driven to long-on for four.", baseTime
        ));

        if ("new_ball".equalsIgnoreCase(scenario) || "newer_score".equalsIgnoreCase(scenario)) {
            // Additional new ball 77
            items.add(new CommentaryDto(
                    "inn1-12.5", 77, 1, 1, 12, 5,
                    "Good length on off stump, defended solidly.", baseTime.plusSeconds(30)
            ));
        }

        return new PollCommentaryResponse("provider-b", matchId, items);
    }

    public static class MockProviderUnavailableException extends RuntimeException {
        public MockProviderUnavailableException(String message) {
            super(message);
        }
    }
}
