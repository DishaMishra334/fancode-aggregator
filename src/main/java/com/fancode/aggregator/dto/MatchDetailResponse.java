package com.fancode.aggregator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MatchDetailResponse(
    String id,
    String status,
    String activeProvider,
    ClientScoreResponse score,
    Instant updatedAt
) {}

