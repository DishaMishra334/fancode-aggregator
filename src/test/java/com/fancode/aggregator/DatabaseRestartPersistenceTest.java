package com.fancode.aggregator;

import com.fancode.aggregator.dto.ClientCommentaryItemResponse;
import com.fancode.aggregator.dto.ClientScoreResponse;
import com.fancode.aggregator.dto.CommentaryDto;
import com.fancode.aggregator.dto.PushEventRequest;
import com.fancode.aggregator.dto.SnapshotDto;
import com.fancode.aggregator.service.AggregatorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseRestartPersistenceTest {

    private static final String DB_FILE_PATH = "./target/test-data/restart_db";

    @Test
    @DisplayName("Case 8: Restart against the same persistent database preserves state")
    void testRestartAgainstSameDatabase() {
        // Ensure test directory exists and old test db is cleared
        File dir = new File("./target/test-data");
        dir.mkdirs();
        new File(dir, "restart_db.mv.db").delete();

        Instant now = Instant.parse("2026-10-22T14:00:00.000Z");

        // PHASE 1: Start Application Instance 1
        ConfigurableApplicationContext ctx1 = new SpringApplicationBuilder(FancodeAggregatorApplication.class)
                .properties(
                        "server.port=0",
                        "spring.datasource.url=jdbc:h2:file:" + DB_FILE_PATH + ";DB_CLOSE_DELAY=-1",
                        "spring.jpa.hibernate.ddl-auto=update",
                        "app.poller.enabled=false",
                        "app.primary-provider=provider-a"
                )
                .run();

        AggregatorService service1 = ctx1.getBean(AggregatorService.class);

        // Ingest data into Instance 1
        PushEventRequest event = new PushEventRequest(
                "provider-a", "restart-d1", now, "a-match-1001",
                new SnapshotDto(5, 1, "live", 1, 45, 1, "5.2", now),
                new CommentaryDto("restart-b1", 1, 1, 1, 5, 2, "Flighted on middle, worked away for one.", now)
        );
        service1.processPushEvent(event);

        ClientScoreResponse score1 = service1.getMatchScore("match-1001");
        assertThat(score1.runs()).isEqualTo(45);
        assertThat(score1.overs()).isEqualTo("5.2");

        // STOP Application Instance 1 (simulating process restart)
        ctx1.close();

        // PHASE 2: Start Application Instance 2 against the EXACT SAME database file
        ConfigurableApplicationContext ctx2 = new SpringApplicationBuilder(FancodeAggregatorApplication.class)
                .properties(
                        "server.port=0",
                        "spring.datasource.url=jdbc:h2:file:" + DB_FILE_PATH + ";DB_CLOSE_DELAY=-1",
                        "spring.jpa.hibernate.ddl-auto=update",
                        "app.poller.enabled=false",
                        "app.primary-provider=provider-a"
                )
                .run();

        AggregatorService service2 = ctx2.getBean(AggregatorService.class);

        // Verify that Instance 2 reads previously stored state intact from disk!
        ClientScoreResponse score2 = service2.getMatchScore("match-1001");
        assertThat(score2.runs()).isEqualTo(45);
        assertThat(score2.wickets()).isEqualTo(1);
        assertThat(score2.overs()).isEqualTo("5.2");

        List<ClientCommentaryItemResponse> commentary2 = service2.getMatchCommentary("match-1001");
        assertThat(commentary2).hasSize(1);
        assertThat(commentary2.get(0).ballId()).isEqualTo("restart-b1");
        assertThat(commentary2.get(0).text()).isEqualTo("Flighted on middle, worked away for one.");

        ctx2.close();
    }
}
