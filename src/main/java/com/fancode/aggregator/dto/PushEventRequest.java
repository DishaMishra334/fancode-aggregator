package com.fancode.aggregator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PushEventRequest(
    String provider,
    String deliveryId,
    Instant occurredAt,
    String providerMatchId,
    SnapshotDto snapshot,
    CommentaryDto commentary
) {}

