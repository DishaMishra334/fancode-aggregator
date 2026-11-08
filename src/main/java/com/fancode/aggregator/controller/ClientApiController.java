package com.fancode.aggregator.controller;

import com.fancode.aggregator.dto.ClientCommentaryItemResponse;
import com.fancode.aggregator.dto.ClientScoreResponse;
import com.fancode.aggregator.dto.MatchDetailResponse;
import com.fancode.aggregator.dto.MatchSummaryResponse;
import com.fancode.aggregator.service.AggregatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/matches")
public class ClientApiController {

    private final AggregatorService aggregatorService;

    public ClientApiController(AggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    /**
     * GET /matches
     * Matches the service knows, with client id, status, and a summary score once one exists.
     */
    @GetMapping
    public ResponseEntity<List<MatchSummaryResponse>> getMatches() {
        return ResponseEntity.ok(aggregatorService.getAllMatches());
    }

    /**
     * GET /matches/{id}
     * One match, including current status and score.
     */
    @GetMapping("/{id}")
    public ResponseEntity<MatchDetailResponse> getMatchDetail(@PathVariable("id") String matchId) {
        return ResponseEntity.ok(aggregatorService.getMatchDetail(matchId));
    }

    /**
     * GET /matches/{id}/score
     * Current status, innings, runs, wickets, and overs.
     */
    @GetMapping("/{id}/score")
    public ResponseEntity<ClientScoreResponse> getMatchScore(@PathVariable("id") String matchId) {
        return ResponseEntity.ok(aggregatorService.getMatchScore(matchId));
    }

    /**
     * GET /matches/{id}/commentary
     * One entry per logical ball, in sequence order, using the newest applied revision and its text.
     */
    @GetMapping("/{id}/commentary")
    public ResponseEntity<List<ClientCommentaryItemResponse>> getMatchCommentary(@PathVariable("id") String matchId) {
        return ResponseEntity.ok(aggregatorService.getMatchCommentary(matchId));
    }
}
