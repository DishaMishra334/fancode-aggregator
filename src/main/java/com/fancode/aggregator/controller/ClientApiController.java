package com.fancode.aggregator.controller;

import com.fancode.aggregator.dto.ClientCommentaryItemResponse;
import com.fancode.aggregator.dto.ClientScoreResponse;
import com.fancode.aggregator.dto.MatchDetailResponse;
import com.fancode.aggregator.dto.MatchSummaryResponse;
import com.fancode.aggregator.service.AggregatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/matches")
@Tag(name = "Client Read APIs", description = "Endpoints for client applications to view matches, live scores, and commentary")
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
    @Operation(summary = "List all known matches", description = "Returns summary list of all known matches with their client ID, status, and summary score.")
    @ApiResponse(responseCode = "200", description = "List of matches retrieved successfully")
    public ResponseEntity<List<MatchSummaryResponse>> getMatches() {
        return ResponseEntity.ok(aggregatorService.getAllMatches());
    }

    /**
     * GET /matches/{id}
     * One match, including current status and score.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get complete match details", description = "Returns full details of a specific match, including active provider and complete score breakdown.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Match found and details returned"),
            @ApiResponse(responseCode = "404", description = "Match ID not found")
    })
    public ResponseEntity<MatchDetailResponse> getMatchDetail(
            @Parameter(description = "Canonical match identifier (e.g. match-1001)", example = "match-1001")
            @PathVariable("id") String matchId) {
        return ResponseEntity.ok(aggregatorService.getMatchDetail(matchId));
    }

    /**
     * GET /matches/{id}/score
     * Current status, innings, runs, wickets, and overs.
     */
    @GetMapping("/{id}/score")
    @Operation(summary = "Get current match score", description = "Returns status, innings, runs, wickets, and overs for the specified match using the winning provider.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Score returned successfully"),
            @ApiResponse(responseCode = "404", description = "Match ID not found")
    })
    public ResponseEntity<ClientScoreResponse> getMatchScore(
            @Parameter(description = "Canonical match identifier (e.g. match-1001)", example = "match-1001")
            @PathVariable("id") String matchId) {
        return ResponseEntity.ok(aggregatorService.getMatchScore(matchId));
    }

    /**
     * GET /matches/{id}/commentary
     * One entry per logical ball, in sequence order, using the newest applied revision and its text.
     */
    @GetMapping("/{id}/commentary")
    @Operation(summary = "Get ball-by-ball commentary", description = "Returns all logical balls for the active provider in strict sequence order with the latest applied revision.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Commentary list returned successfully"),
            @ApiResponse(responseCode = "404", description = "Match ID not found")
    })
    public ResponseEntity<List<ClientCommentaryItemResponse>> getMatchCommentary(
            @Parameter(description = "Canonical match identifier (e.g. match-1001)", example = "match-1001")
            @PathVariable("id") String matchId) {
        return ResponseEntity.ok(aggregatorService.getMatchCommentary(matchId));
    }
}
