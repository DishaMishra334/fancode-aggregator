package com.fancode.aggregator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientScoreResponse(
    String status,
    int innings,
    int runs,
    int wickets,
    String overs,
    String scoreFormatted,
    String provider
) {}

