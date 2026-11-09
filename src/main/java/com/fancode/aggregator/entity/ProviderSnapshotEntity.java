package com.fancode.aggregator.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "provider_snapshots",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_match_provider_snapshot", columnNames = {"match_id", "provider"})
    }
)
public class ProviderSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false, length = 64)
    private String matchId;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "sequence_num", nullable = false)
    private long sequence;

    @Column(name = "revision_num", nullable = false)
    private int revision;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "innings", nullable = false)
    private int innings;

    @Column(name = "runs", nullable = false)
    private int runs;

    @Column(name = "wickets", nullable = false)
    private int wickets;

    @Column(name = "overs", nullable = false, length = 32)
    private String overs;

    @Column(name = "as_of")
    private Instant asOf;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ProviderSnapshotEntity() {
    }

    public ProviderSnapshotEntity(String matchId, String provider, long sequence, int revision,
                                  String status, int innings, int runs, int wickets,
                                  String overs, Instant asOf) {
        this.matchId = matchId;
        this.provider = provider;
        this.sequence = sequence;
        this.revision = revision;
        this.status = status;
        this.innings = innings;
        this.runs = runs;
        this.wickets = wickets;
        this.overs = overs;
        this.asOf = asOf;
        this.updatedAt = Instant.now();
    }

    /**
     * Strictly newer check:
     * (newSeq > currentSeq) || (newSeq == currentSeq && newRev > currentRev)
     */
    public boolean isStrictlyNewerThan(long otherSeq, int otherRev) {
        if (this.sequence > otherSeq) {
            return true;
        }
        return this.sequence == otherSeq && this.revision > otherRev;
    }

    public static boolean isCandidateStrictlyNewer(long candidateSeq, int candidateRev, long storedSeq, int storedRev) {
        if (candidateSeq > storedSeq) {
            return true;
        }
        return candidateSeq == storedSeq && candidateRev > storedRev;
    }

    public Long getId() {
        return id;
    }

    public String getMatchId() {
        return matchId;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getInnings() {
        return innings;
    }

    public void setInnings(int innings) {
        this.innings = innings;
    }

    public int getRuns() {
        return runs;
    }

    public void setRuns(int runs) {
        this.runs = runs;
    }

    public int getWickets() {
        return wickets;
    }

    public void setWickets(int wickets) {
        this.wickets = wickets;
    }

    public String getOvers() {
        return overs;
    }

    public void setOvers(String overs) {
        this.overs = overs;
    }

    public Instant getAsOf() {
        return asOf;
    }

    public void setAsOf(Instant asOf) {
        this.asOf = asOf;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

