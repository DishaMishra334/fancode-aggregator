package com.fancode.aggregator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientCommentaryItemResponse(
    String ballId,
    long sequence,
    int revision,
    int innings,
    int over,
    int ball,
    String text,
    Instant occurredAt
) {}
