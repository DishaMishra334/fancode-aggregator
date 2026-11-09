package com.fancode.aggregator.service;

import com.fancode.aggregator.config.AppProperties;
import com.fancode.aggregator.dto.PollCommentaryResponse;
import com.fancode.aggregator.dto.PollScoreResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ProviderBPollerService {

    private static final Logger log = LoggerFactory.getLogger(ProviderBPollerService.class);

    private final AggregatorService aggregatorService;
    private final MockProviderBService mockProviderBService;
    private final AppProperties appProperties;

    private int pollCount = 0;
    private int errorCount = 0;

    public ProviderBPollerService(AggregatorService aggregatorService,
                                  MockProviderBService mockProviderBService,
                                  AppProperties appProperties) {
        this.aggregatorService = aggregatorService;
        this.mockProviderBService = mockProviderBService;
        this.appProperties = appProperties;
    }

    @Scheduled(fixedDelayString = "${app.poller.interval-ms:5000}")
    public void scheduledPoll() {
        if (!appProperties.getPoller().isEnabled()) {
            return;
        }
        pollOnce(appProperties.getPoller().getMatchId());
    }

    /**
     * Executes one poll cycle against Provider B.
     * Gracefully catches temporary provider errors (e.g. 503) and leaves stored match intact.
     */
    public boolean pollOnce(String providerMatchId) {
        if (providerMatchId == null) {
            providerMatchId = appProperties.getPoller().getMatchId();
        }

        pollCount++;
        try {
            log.info("Polling Provider B for match [{}] (Poll #{})", providerMatchId, pollCount);

            // 1. Fetch score
            PollScoreResponse scoreResponse = mockProviderBService.getScore(providerMatchId);
            if (scoreResponse != null && scoreResponse.snapshot() != null) {
                aggregatorService.processPollScore(
                        scoreResponse.provider(),
                        scoreResponse.providerMatchId(),
                        scoreResponse.snapshot()
                );
            }

            // 2. Fetch commentary
            PollCommentaryResponse commentaryResponse = mockProviderBService.getCommentary(providerMatchId);
            if (commentaryResponse != null && commentaryResponse.items() != null) {
                aggregatorService.processPollCommentary(
                        commentaryResponse.provider(),
                        commentaryResponse.providerMatchId(),
                        commentaryResponse.items()
                );
            }

            log.info("Provider B poll successful for match [{}]", providerMatchId);
            return true;
        } catch (MockProviderBService.MockProviderUnavailableException ex) {
            errorCount++;
            log.warn("Provider B temporary error encountered during poll #{}: {}. Preserving last good state.",
                    pollCount, ex.getMessage());
            return false;
        } catch (Exception ex) {
            errorCount++;
            log.error("Unexpected error polling Provider B: {}. Preserving last good state.", ex.getMessage());
            return false;
        }
    }

    public int getPollCount() {
        return pollCount;
    }

    public int getErrorCount() {
        return errorCount;
    }
}

