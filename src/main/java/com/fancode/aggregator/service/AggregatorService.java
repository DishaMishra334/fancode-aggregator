package com.fancode.aggregator.service;

import com.fancode.aggregator.config.AppProperties;
import com.fancode.aggregator.dto.*;
import com.fancode.aggregator.entity.*;
import com.fancode.aggregator.exception.ResourceNotFoundException;
import com.fancode.aggregator.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AggregatorService {

    private static final Logger log = LoggerFactory.getLogger(AggregatorService.class);

    private final MatchRepository matchRepository;
    private final ProviderDeliveryRepository deliveryRepository;
    private final ProviderSnapshotRepository snapshotRepository;
    private final ProviderCommentaryRepository commentaryRepository;
    private final AppProperties appProperties;

    // Concurrency lock object for match ingestion
    private final Object ingestionLock = new Object();

    public AggregatorService(MatchRepository matchRepository,
                             ProviderDeliveryRepository deliveryRepository,
                             ProviderSnapshotRepository snapshotRepository,
                             ProviderCommentaryRepository commentaryRepository,
                             AppProperties appProperties) {
        this.matchRepository = matchRepository;
        this.deliveryRepository = deliveryRepository;
        this.snapshotRepository = snapshotRepository;
        this.commentaryRepository = commentaryRepository;
        this.appProperties = appProperties;
    }

    /**
     * Ingests a push webhook event from Provider A (or any push provider).
     * Enforces idempotency via deliveryId, then applies snapshot and commentary rules.
     */
    @Transactional
    public PushEventResponse processPushEvent(PushEventRequest event) {
        if (event == null || event.provider() == null || event.deliveryId() == null) {
            throw new IllegalArgumentException("Invalid push event: provider and deliveryId are required");
        }

        synchronized (ingestionLock) {
            // 1. Idempotency Check
            if (deliveryRepository.existsByProviderAndDeliveryId(event.provider(), event.deliveryId())) {
                log.info("Duplicate delivery detected for provider [{}] deliveryId [{}]. State unchanged.",
                        event.provider(), event.deliveryId());
                return PushEventResponse.duplicateIgnored(event.deliveryId());
            }

            // Record delivery ID immediately
            deliveryRepository.save(new ProviderDeliveryEntity(event.provider(), event.deliveryId()));

            // Resolve canonical match ID
            String matchId = appProperties.resolveCanonicalMatchId(event.providerMatchId());
            ensureMatchExists(matchId);

            // 2. Apply snapshot if present
            if (event.snapshot() != null) {
                applySnapshotInternal(matchId, event.provider(), event.snapshot());
            }

            // 3. Apply commentary if present
            if (event.commentary() != null) {
                applyCommentaryInternal(matchId, event.provider(), event.commentary());
            }

            return PushEventResponse.accepted(event.deliveryId());
        }
    }

    /**
     * Ingests a poll response from Provider B.
     * Evaluates snapshot monotonicity and commentary revision rules.
     */
    @Transactional
    public void processPollScore(String provider, String providerMatchId, SnapshotDto snapshot) {
        if (snapshot == null) return;
        synchronized (ingestionLock) {
            String matchId = appProperties.resolveCanonicalMatchId(providerMatchId);
            ensureMatchExists(matchId);
            applySnapshotInternal(matchId, provider, snapshot);
        }
    }

    @Transactional
    public void processPollCommentary(String provider, String providerMatchId, List<CommentaryDto> items) {
        if (items == null || items.isEmpty()) return;
        synchronized (ingestionLock) {
            String matchId = appProperties.resolveCanonicalMatchId(providerMatchId);
            ensureMatchExists(matchId);
            for (CommentaryDto item : items) {
                applyCommentaryInternal(matchId, provider, item);
            }
        }
    }

    /**
     * Applies snapshot if and only if (sequence, revision) is strictly newer than stored for that provider.
     */
    private void applySnapshotInternal(String matchId, String provider, SnapshotDto snapshot) {
        Optional<ProviderSnapshotEntity> existingOpt = snapshotRepository.findByMatchIdAndProvider(matchId, provider);

        if (existingOpt.isPresent()) {
            ProviderSnapshotEntity existing = existingOpt.get();
            boolean strictlyNewer = ProviderSnapshotEntity.isCandidateStrictlyNewer(
                    snapshot.sequence(), snapshot.revision(),
                    existing.getSequence(), existing.getRevision()
            );

            if (!strictlyNewer) {
                log.info("Snapshot candidate ({}, {}) from [{}] for match [{}] is NOT strictly newer than stored ({}, {}). Ignoring.",
                        snapshot.sequence(), snapshot.revision(), provider, matchId,
                        existing.getSequence(), existing.getRevision());
                return;
            }

            // Update existing snapshot
            existing.setSequence(snapshot.sequence());
            existing.setRevision(snapshot.revision());
            existing.setStatus(snapshot.status());
            existing.setInnings(snapshot.innings());
            existing.setRuns(snapshot.runs());
            existing.setWickets(snapshot.wickets());
            existing.setOvers(snapshot.overs());
            existing.setAsOf(snapshot.asOf());
            existing.setUpdatedAt(Instant.now());
            snapshotRepository.save(existing);
            log.info("Snapshot updated for match [{}] provider [{}] -> ({}, {}) Score: {}/{} ({})",
                    matchId, provider, snapshot.sequence(), snapshot.revision(),
                    snapshot.runs(), snapshot.wickets(), snapshot.overs());
        } else {
            // First snapshot for this provider
            ProviderSnapshotEntity entity = new ProviderSnapshotEntity(
                    matchId, provider, snapshot.sequence(), snapshot.revision(),
                    snapshot.status(), snapshot.innings(), snapshot.runs(), snapshot.wickets(),
                    snapshot.overs(), snapshot.asOf()
            );
            snapshotRepository.save(entity);
            log.info("Initial snapshot stored for match [{}] provider [{}] -> ({}, {}) Score: {}/{} ({})",
                    matchId, provider, snapshot.sequence(), snapshot.revision(),
                    snapshot.runs(), snapshot.wickets(), snapshot.overs());
        }

        // Update top-level match entity status and active provider according to precedence
        updateMatchPrecedence(matchId);
    }

    /**
     * Applies commentary item. A higher revision replaces stored text. Equal/lower leaves text untouched.
     */
    private void applyCommentaryInternal(String matchId, String provider, CommentaryDto dto) {
        Optional<ProviderCommentaryEntity> existingOpt =
                commentaryRepository.findByMatchIdAndProviderAndBallId(matchId, provider, dto.ballId());

        if (existingOpt.isPresent()) {
            ProviderCommentaryEntity existing = existingOpt.get();
            if (dto.revision() > existing.getRevision()) {
                existing.setText(dto.text());
                existing.setRevision(dto.revision());
                existing.setSequence(dto.sequence());
                existing.setInnings(dto.innings());
                existing.setOver(dto.over());
                existing.setBall(dto.ball());
                existing.setOccurredAt(dto.occurredAt());
                existing.setUpdatedAt(Instant.now());
                commentaryRepository.save(existing);
                log.info("Commentary ball [{}] from [{}] replaced with higher revision [{}] -> \"{}\"",
                        dto.ballId(), provider, dto.revision(), dto.text());
            } else {
                log.info("Commentary ball [{}] from [{}] candidate revision [{}] is not newer than stored [{}]. Ignored.",
                        dto.ballId(), provider, dto.revision(), existing.getRevision());
            }
        } else {
            // New ball commentary item
            ProviderCommentaryEntity entity = new ProviderCommentaryEntity(
                    matchId, provider, dto.ballId(),
                    dto.sequence(), dto.revision(), dto.innings(),
                    dto.over(), dto.ball(), dto.text(), dto.occurredAt()
            );
            commentaryRepository.save(entity);
            log.info("New commentary ball [{}] inserted for match [{}] provider [{}] seq [{}] -> \"{}\"",
                    dto.ballId(), matchId, provider, dto.sequence(), dto.text());
        }
    }

    private void ensureMatchExists(String matchId) {
        if (!matchRepository.existsById(matchId)) {
            MatchEntity match = new MatchEntity(matchId, "scheduled");
            matchRepository.save(match);
        }
    }

    private void updateMatchPrecedence(String matchId) {
        MatchEntity match = matchRepository.findById(matchId).orElse(new MatchEntity(matchId, "scheduled"));
        Optional<ProviderSnapshotEntity> activeSnapshotOpt = determineActiveSnapshot(matchId);
        if (activeSnapshotOpt.isPresent()) {
            ProviderSnapshotEntity active = activeSnapshotOpt.get();
            match.setStatus(active.getStatus());
            match.setActiveProvider(active.getProvider());
            match.setUpdatedAt(Instant.now());
            matchRepository.save(match);
        }
    }

    /**
     * Deterministic precedence rule:
     * 1. Primary provider (appProperties.primaryProvider, default 'provider-a') is checked first.
     * 2. If primary provider has a stored snapshot, primary wins.
     * 3. If primary provider has no snapshot, secondary provider ('provider-b') is used.
     * Note: Sequence numbers are NEVER compared across providers.
     */
    public Optional<ProviderSnapshotEntity> determineActiveSnapshot(String matchId) {
        String primary = appProperties.getPrimaryProvider();
        Optional<ProviderSnapshotEntity> primaryOpt = snapshotRepository.findByMatchIdAndProvider(matchId, primary);
        if (primaryOpt.isPresent()) {
            return primaryOpt;
        }

        // Secondary fallback
        String secondary = "provider-a".equalsIgnoreCase(primary) ? "provider-b" : "provider-a";
        return snapshotRepository.findByMatchIdAndProvider(matchId, secondary);
    }

    /**
     * Determine active provider for commentary:
     * Follows the same precedence rule as score snapshots.
     */
    public String determineActiveProvider(String matchId) {
        Optional<ProviderSnapshotEntity> activeSnap = determineActiveSnapshot(matchId);
        if (activeSnap.isPresent()) {
            return activeSnap.get().getProvider();
        }
        return appProperties.getPrimaryProvider();
    }

    // ==========================================
    // Client Read APIs
    // ==========================================

    @Transactional(readOnly = true)
    public List<MatchSummaryResponse> getAllMatches() {
        List<MatchEntity> matches = matchRepository.findAll();
        List<MatchSummaryResponse> list = new ArrayList<>();
        for (MatchEntity match : matches) {
            String summaryScore = "N/A";
            Optional<ProviderSnapshotEntity> snapshotOpt = determineActiveSnapshot(match.getMatchId());
            if (snapshotOpt.isPresent()) {
                ProviderSnapshotEntity s = snapshotOpt.get();
                summaryScore = String.format("%d/%d (%s)", s.getRuns(), s.getWickets(), s.getOvers());
            }
            list.add(new MatchSummaryResponse(match.getMatchId(), match.getStatus(), summaryScore));
        }
        return list;
    }

    @Transactional(readOnly = true)
    public MatchDetailResponse getMatchDetail(String matchId) {
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));

        ClientScoreResponse scoreResponse = getMatchScore(matchId);
        String activeProvider = scoreResponse != null ? scoreResponse.provider() : match.getActiveProvider();

        return new MatchDetailResponse(
                match.getMatchId(),
                match.getStatus(),
                activeProvider,
                scoreResponse,
                match.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public ClientScoreResponse getMatchScore(String matchId) {
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));

        Optional<ProviderSnapshotEntity> snapshotOpt = determineActiveSnapshot(matchId);
        if (snapshotOpt.isEmpty()) {
            return new ClientScoreResponse(match.getStatus(), 0, 0, 0, "0.0", "0/0", null);
        }

        ProviderSnapshotEntity s = snapshotOpt.get();
        String formatted = String.format("%d/%d", s.getRuns(), s.getWickets());
        return new ClientScoreResponse(
                s.getStatus(),
                s.getInnings(),
                s.getRuns(),
                s.getWickets(),
                s.getOvers(),
                formatted,
                s.getProvider()
        );
    }

    @Transactional(readOnly = true)
    public List<ClientCommentaryItemResponse> getMatchCommentary(String matchId) {
        if (!matchRepository.existsById(matchId)) {
            throw new ResourceNotFoundException("Match not found: " + matchId);
        }

        String activeProvider = determineActiveProvider(matchId);
        List<ProviderCommentaryEntity> entities =
                commentaryRepository.findByMatchIdAndProviderOrderBySequenceAsc(matchId, activeProvider);

        return entities.stream()
                .map(e -> new ClientCommentaryItemResponse(
                        e.getBallId(),
                        e.getSequence(),
                        e.getRevision(),
                        e.getInnings(),
                        e.getOver(),
                        e.getBall(),
                        e.getText(),
                        e.getOccurredAt()
                ))
                .toList();
    }
}
