package com.fancode.aggregator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SnapshotDto(
    long sequence,
    int revision,
    String status,
    int innings,
    int runs,
    int wickets,
    String overs,
    Instant asOf
) {}

