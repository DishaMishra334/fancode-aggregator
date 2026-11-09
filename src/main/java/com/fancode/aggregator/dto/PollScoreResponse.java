package com.fancode.aggregator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PollScoreResponse(
    String provider,
    String providerMatchId,
    SnapshotDto snapshot
) {}

