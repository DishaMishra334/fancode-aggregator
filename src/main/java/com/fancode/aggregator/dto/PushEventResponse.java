package com.fancode.aggregator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PushEventResponse(
    String status,
    String deliveryId,
    String message
) {
    public static PushEventResponse accepted(String deliveryId) {
        return new PushEventResponse("accepted", deliveryId, "Event processed successfully");
    }

    public static PushEventResponse duplicateIgnored(String deliveryId) {
        return new PushEventResponse("duplicate_ignored", deliveryId, "Delivery ID already processed, ignored as idempotent duplicate");
    }
}

