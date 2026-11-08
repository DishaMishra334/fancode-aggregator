package com.fancode.aggregator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PollCommentaryResponse(
    String provider,
    String providerMatchId,
    List<CommentaryDto> items
) {}
