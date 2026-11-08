package com.fancode.aggregator;

import com.fancode.aggregator.dto.*;
import com.fancode.aggregator.entity.ProviderCommentaryEntity;
import com.fancode.aggregator.entity.ProviderSnapshotEntity;
import com.fancode.aggregator.repository.MatchRepository;
import com.fancode.aggregator.repository.ProviderCommentaryRepository;
import com.fancode.aggregator.repository.ProviderDeliveryRepository;
import com.fancode.aggregator.repository.ProviderSnapshotRepository;
import com.fancode.aggregator.service.AggregatorService;
import com.fancode.aggregator.service.MockProviderBService;
import com.fancode.aggregator.service.ProviderAGeneratorService;
import com.fancode.aggregator.service.ProviderBPollerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.poller.enabled=false",
        "app.primary-provider=provider-a"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class AggregatorIntegrationTests {

    @Autowired
    private AggregatorService aggregatorService;

    @Autowired
    private ProviderAGeneratorService generatorService;

    @Autowired
    private ProviderBPollerService pollerService;

    @Autowired
    private MockProviderBService mockProviderBService;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private ProviderDeliveryRepository deliveryRepository;

    @Autowired
    private ProviderSnapshotRepository snapshotRepository;

    @Autowired
    private ProviderCommentaryRepository commentaryRepository;

    private static final String MATCH_ID = "match-1001";
    private static final String PROVIDER_A_MATCH = "a-match-1001";
    private static final String PROVIDER_B_MATCH = "b-match-1001";
    private final Instant now = Instant.parse("2026-10-22T14:00:00.000Z");

    @BeforeEach
    void setUp() {
        commentaryRepository.deleteAll();
        snapshotRepository.deleteAll();
        deliveryRepository.deleteAll();
        matchRepository.deleteAll();
        mockProviderBService.setScenario("initial");
    }

    @Test
    @DisplayName("Case 1: Ordered Ingestion from Push Provider")
    void testOrderedIngestion() {
        // Step 1: Ingest d1
        PushEventRequest d1 = new PushEventRequest(
                "provider-a", "d1", now, PROVIDER_A_MATCH,
                new SnapshotDto(1, 1, "live", 1, 4, 0, "0.4", now),
                new CommentaryDto("b1", 1, 1, 1, 0, 4, "four", now)
        );
        PushEventResponse r1 = aggregatorService.processPushEvent(d1);
        assertThat(r1.status()).isEqualTo("accepted");

        ClientScoreResponse score1 = aggregatorService.getMatchScore(MATCH_ID);
        assertThat(score1.runs()).isEqualTo(4);
        assertThat(score1.wickets()).isEqualTo(0);
        assertThat(score1.overs()).isEqualTo("0.4");

        // Step 2: Ingest d2 (higher sequence)
        PushEventRequest d2 = new PushEventRequest(
                "provider-a", "d2", now.plusSeconds(30), PROVIDER_A_MATCH,
                new SnapshotDto(2, 1, "live", 1, 4, 1, "0.5", now.plusSeconds(30)),
                new CommentaryDto("b2", 2, 1, 1, 0, 5, "out", now.plusSeconds(30))
        );
        PushEventResponse r2 = aggregatorService.processPushEvent(d2);
        assertThat(r2.status()).isEqualTo("accepted");

        ClientScoreResponse score2 = aggregatorService.getMatchScore(MATCH_ID);
        assertThat(score2.runs()).isEqualTo(4);
        assertThat(score2.wickets()).isEqualTo(1);
        assertThat(score2.overs()).isEqualTo("0.5");

        List<ClientCommentaryItemResponse> commentary = aggregatorService.getMatchCommentary(MATCH_ID);
        assertThat(commentary).hasSize(2);
        assertThat(commentary.get(0).ballId()).isEqualTo("b1");
        assertThat(commentary.get(1).ballId()).isEqualTo("b2");
    }

    @Test
    @DisplayName("Case 2: Duplicate Push Delivery leaves state unchanged")
    void testDuplicatePushDelivery() {
        PushEventRequest d1 = new PushEventRequest(
                "provider-a", "d1", now, PROVIDER_A_MATCH,
                new SnapshotDto(1, 1, "live", 1, 4, 0, "0.4", now),
                new CommentaryDto("b1", 1, 1, 1, 0, 4, "four", now)
        );
        PushEventResponse r1 = aggregatorService.processPushEvent(d1);
        assertThat(r1.status()).isEqualTo("accepted");

        // Send d1 again
        PushEventResponse r1Duplicate = aggregatorService.processPushEvent(d1);
        assertThat(r1Duplicate.status()).isEqualTo("duplicate_ignored");

        // Verify commentary still only has 1 item and score is unchanged
        List<ClientCommentaryItemResponse> commentary = aggregatorService.getMatchCommentary(MATCH_ID);
        assertThat(commentary).hasSize(1);
        assertThat(commentary.get(0).text()).isEqualTo("four");
    }

    @Test
    @DisplayName("Case 3: Older snapshot arriving after newer one; late ball returned in sequence")
    void testOlderSnapshotWithLateBallReturnedInSequence() {
        // Step 1: d1 (seq 1)
        aggregatorService.processPushEvent(new PushEventRequest(
                "provider-a", "d1", now, PROVIDER_A_MATCH,
                new SnapshotDto(1, 1, "live", 1, 4, 0, "0.4", now),
                new CommentaryDto("b1", 1, 1, 1, 0, 4, "four", now)
        ));

        // Step 2: d2 (seq 2)
        aggregatorService.processPushEvent(new PushEventRequest(
                "provider-a", "d2", now.plusSeconds(20), PROVIDER_A_MATCH,
                new SnapshotDto(2, 1, "live", 1, 4, 1, "0.5", now.plusSeconds(20)),
                new CommentaryDto("b2", 2, 1, 1, 0, 5, "out", now.plusSeconds(20))
        ));

        // Step 4: d4 arrives BEFORE d3! Snapshot (4, 1) = 10/1 in 1.1, ball b4
        aggregatorService.processPushEvent(new PushEventRequest(
                "provider-a", "d4", now.plusSeconds(60), PROVIDER_A_MATCH,
                new SnapshotDto(4, 1, "live", 1, 10, 1, "1.1", now.plusSeconds(60)),
                new CommentaryDto("b4", 4, 1, 1, 1, 1, "six", now.plusSeconds(60))
        ));

        // Score immediately reflects 10/1
        ClientScoreResponse scoreAfterD4 = aggregatorService.getMatchScore(MATCH_ID);
        assertThat(scoreAfterD4.runs()).isEqualTo(10);
        assertThat(scoreAfterD4.overs()).isEqualTo("1.1");

        // Step 5: Delayed d3 arrives with older snapshot (3, 1) = 8/1 in 1.0, ball b3
        aggregatorService.processPushEvent(new PushEventRequest(
                "provider-a", "d3", now.plusSeconds(40), PROVIDER_A_MATCH,
                new SnapshotDto(3, 1, "live", 1, 8, 1, "1.0", now.plusSeconds(40)),
                new CommentaryDto("b3", 3, 1, 1, 1, 0, "two runs", now.plusSeconds(40))
        ));

        // Older snapshot (3, 1) must NOT overwrite newer snapshot (4, 1)
        ClientScoreResponse scoreAfterD3 = aggregatorService.getMatchScore(MATCH_ID);
        assertThat(scoreAfterD3.runs()).isEqualTo(10);
        assertThat(scoreAfterD3.overs()).isEqualTo("1.1");

        // But late ball b3 MUST be inserted in sequence order: b1(1), b2(2), b3(3), b4(4)
        List<ClientCommentaryItemResponse> commentary = aggregatorService.getMatchCommentary(MATCH_ID);
        assertThat(commentary).hasSize(4);
        assertThat(commentary.get(0).ballId()).isEqualTo("b1");
        assertThat(commentary.get(1).ballId()).isEqualTo("b2");
        assertThat(commentary.get(2).ballId()).isEqualTo("b3");
        assertThat(commentary.get(3).ballId()).isEqualTo("b4");
        assertThat(commentary.get(2).text()).isEqualTo("two runs");
    }

    @Test
    @DisplayName("Case 4: Commentary Correction (higher revision replaces text, lower revision ignored)")
    void testCommentaryCorrection() {
        // Initial ball b2 with rev 1
        aggregatorService.processPushEvent(new PushEventRequest(
                "provider-a", "d2", now, PROVIDER_A_MATCH,
                new SnapshotDto(2, 1, "live", 1, 4, 1, "0.5", now),
                new CommentaryDto("b2", 2, 1, 1, 0, 5, "out", now)
        ));

        List<ClientCommentaryItemResponse> com1 = aggregatorService.getMatchCommentary(MATCH_ID);
        assertThat(com1.get(0).text()).isEqualTo("out");
        assertThat(com1.get(0).revision()).isEqualTo(1);

        // Correction: new deliveryId d5, same ballId b2, higher revision 2
        aggregatorService.processPushEvent(new PushEventRequest(
                "provider-a", "d5", now.plusSeconds(30), PROVIDER_A_MATCH,
                new SnapshotDto(2, 1, "live", 1, 4, 1, "0.5", now.plusSeconds(30)),
                new CommentaryDto("b2", 2, 2, 1, 0, 5, "out, review stays out", now.plusSeconds(30))
        ));

        List<ClientCommentaryItemResponse> com2 = aggregatorService.getMatchCommentary(MATCH_ID);
        assertThat(com2).hasSize(1);
        assertThat(com2.get(0).text()).isEqualTo("out, review stays out");
        assertThat(com2.get(0).revision()).isEqualTo(2);

        // Send an outdated revision 1 for b2: must be ignored
        aggregatorService.processPushEvent(new PushEventRequest(
                "provider-a", "d6", now.plusSeconds(40), PROVIDER_A_MATCH,
                null,
                new CommentaryDto("b2", 2, 1, 1, 0, 5, "out (stale revision)", now.plusSeconds(40))
        ));

        List<ClientCommentaryItemResponse> com3 = aggregatorService.getMatchCommentary(MATCH_ID);
        assertThat(com3.get(0).text()).isEqualTo("out, review stays out");
        assertThat(com3.get(0).revision()).isEqualTo(2);
    }

    @Test
    @DisplayName("Case 5: Repeated Identical Polls do not duplicate balls")
    void testRepeatedIdenticalPolls() {
        mockProviderBService.setScenario("unchanged");

        // Poll multiple times
        boolean p1 = pollerService.pollOnce(PROVIDER_B_MATCH);
        boolean p2 = pollerService.pollOnce(PROVIDER_B_MATCH);
        boolean p3 = pollerService.pollOnce(PROVIDER_B_MATCH);

        assertTrue(p1 && p2 && p3);

        // Verify provider-b commentary in DB has exactly 1 entry for ball inn1-12.4
        List<ProviderCommentaryEntity> list =
                commentaryRepository.findByMatchIdAndProviderOrderBySequenceAsc(MATCH_ID, "provider-b");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getBallId()).isEqualTo("inn1-12.4");
        assertThat(list.get(0).getText()).isEqualTo("Full and straight, driven to long-on for four.");
    }

    @Test
    @DisplayName("Case 6: Stale Poll response loses to newer stored snapshot")
    void testStalePoll() {
        // Step 1: Poll newer score (sequence 16, 104/2)
        mockProviderBService.setScenario("newer_score");
        pollerService.pollOnce(PROVIDER_B_MATCH);

        Optional<ProviderSnapshotEntity> bSnap = snapshotRepository.findByMatchIdAndProvider(MATCH_ID, "provider-b");
        assertTrue(bSnap.isPresent());
        assertThat(bSnap.get().getSequence()).isEqualTo(16);
        assertThat(bSnap.get().getRuns()).isEqualTo(104);

        // Step 2: Poll stale score (sequence 10, 80/1)
        mockProviderBService.setScenario("stale");
        pollerService.pollOnce(PROVIDER_B_MATCH);

        Optional<ProviderSnapshotEntity> bSnapAfterStale = snapshotRepository.findByMatchIdAndProvider(MATCH_ID, "provider-b");
        assertTrue(bSnapAfterStale.isPresent());
        // Must still be sequence 16, 104 runs
        assertThat(bSnapAfterStale.get().getSequence()).isEqualTo(16);
        assertThat(bSnapAfterStale.get().getRuns()).isEqualTo(104);
    }

    @Test
    @DisplayName("Case 7: Provider Error (HTTP 503) leaves last good state in place")
    void testProviderErrorLeavesLastGoodState() {
        // Setup initial good state from Provider B
        mockProviderBService.setScenario("initial");
        pollerService.pollOnce(PROVIDER_B_MATCH);

        Optional<ProviderSnapshotEntity> before = snapshotRepository.findByMatchIdAndProvider(MATCH_ID, "provider-b");
        assertTrue(before.isPresent());
        assertThat(before.get().getRuns()).isEqualTo(98);

        // Simulate 503 Service Unavailable
        mockProviderBService.setScenario("error_503");
        boolean success = pollerService.pollOnce(PROVIDER_B_MATCH);
        assertFalse(success, "Poll should fail gracefully on 503");

        // Verify last good snapshot is still untouched
        Optional<ProviderSnapshotEntity> after = snapshotRepository.findByMatchIdAndProvider(MATCH_ID, "provider-b");
        assertTrue(after.isPresent());
        assertThat(after.get().getRuns()).isEqualTo(98);
        assertThat(after.get().getOvers()).isEqualTo("12.4");
    }

    @Test
    @DisplayName("Case 8: Full worked push scenario matches spec exactly")
    void testFullWorkedPushScenario() {
        List<PushEventResponse> responses = generatorService.runWorkedPushScenario(PROVIDER_A_MATCH);
        assertThat(responses).hasSize(6);
        assertThat(responses.get(0).status()).isEqualTo("accepted");
        assertThat(responses.get(1).status()).isEqualTo("accepted");
        assertThat(responses.get(2).status()).isEqualTo("duplicate_ignored"); // step 3 was d2 again
        assertThat(responses.get(3).status()).isEqualTo("accepted");
        assertThat(responses.get(4).status()).isEqualTo("accepted");
        assertThat(responses.get(5).status()).isEqualTo("accepted");

        // Verify final score is 10/1
        ClientScoreResponse finalScore = aggregatorService.getMatchScore(MATCH_ID);
        assertThat(finalScore.runs()).isEqualTo(10);
        assertThat(finalScore.wickets()).isEqualTo(1);
        assertThat(finalScore.overs()).isEqualTo("1.1");

        // Verify commentary has all 4 balls, with b2 having corrected text
        List<ClientCommentaryItemResponse> commentary = aggregatorService.getMatchCommentary(MATCH_ID);
        assertThat(commentary).hasSize(4);
        assertThat(commentary.get(0).ballId()).isEqualTo("b1");
        assertThat(commentary.get(1).ballId()).isEqualTo("b2");
        assertThat(commentary.get(1).text()).isEqualTo("out, review stays out");
        assertThat(commentary.get(1).revision()).isEqualTo(2);
        assertThat(commentary.get(2).ballId()).isEqualTo("b3");
        assertThat(commentary.get(3).ballId()).isEqualTo("b4");
    }
}
