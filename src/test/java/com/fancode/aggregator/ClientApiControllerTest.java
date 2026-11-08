package com.fancode.aggregator;

import com.fancode.aggregator.dto.CommentaryDto;
import com.fancode.aggregator.dto.PushEventRequest;
import com.fancode.aggregator.dto.SnapshotDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:controllertestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.poller.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class ClientApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Read APIs return 404 for unknown match ID")
    void testUnknownMatchReturns404() throws Exception {
        mockMvc.perform(get("/matches/non-existent-match"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("Match not found: non-existent-match")))
                .andExpect(jsonPath("$.status", is(404)));

        mockMvc.perform(get("/matches/non-existent-match/score"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/matches/non-existent-match/commentary"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Webhook POST processes push event and client APIs serve normalized data")
    void testWebhookAndClientReadFlow() throws Exception {
        Instant now = Instant.parse("2026-10-22T14:03:11.000Z");

        PushEventRequest request = new PushEventRequest(
                "provider-a",
                "d-1042",
                now,
                "a-match-1001",
                new SnapshotDto(42, 1, "live", 1, 98, 2, "12.4", now),
                new CommentaryDto("inn1-12.4", 42, 1, 1, 12, 4,
                        "Full and straight, driven to long-on for four.", now)
        );

        // 1. Send Webhook
        mockMvc.perform(post("/api/webhooks/provider-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("accepted")))
                .andExpect(jsonPath("$.deliveryId", is("d-1042")));

        // 2. Query GET /matches
        mockMvc.perform(get("/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is("match-1001")))
                .andExpect(jsonPath("$[0].status", is("live")))
                .andExpect(jsonPath("$[0].summaryScore", is("98/2 (12.4)")));

        // 3. Query GET /matches/{id}
        mockMvc.perform(get("/matches/match-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("match-1001")))
                .andExpect(jsonPath("$.status", is("live")))
                .andExpect(jsonPath("$.activeProvider", is("provider-a")))
                .andExpect(jsonPath("$.score.runs", is(98)))
                .andExpect(jsonPath("$.score.wickets", is(2)))
                .andExpect(jsonPath("$.score.overs", is("12.4")));

        // 4. Query GET /matches/{id}/score
        mockMvc.perform(get("/matches/match-1001/score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("live")))
                .andExpect(jsonPath("$.innings", is(1)))
                .andExpect(jsonPath("$.runs", is(98)))
                .andExpect(jsonPath("$.wickets", is(2)))
                .andExpect(jsonPath("$.overs", is("12.4")))
                .andExpect(jsonPath("$.scoreFormatted", is("98/2")));

        // 5. Query GET /matches/{id}/commentary
        mockMvc.perform(get("/matches/match-1001/commentary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ballId", is("inn1-12.4")))
                .andExpect(jsonPath("$[0].sequence", is(42)))
                .andExpect(jsonPath("$[0].revision", is(1)))
                .andExpect(jsonPath("$[0].text", is("Full and straight, driven to long-on for four.")));

        // 6. Resend the exact same delivery -> duplicate_ignored
        mockMvc.perform(post("/api/webhooks/provider-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("duplicate_ignored")))
                .andExpect(jsonPath("$.deliveryId", is("d-1042")));
    }
}
