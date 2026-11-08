package com.fancode.aggregator.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "provider_commentaries",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_match_provider_ball", columnNames = {"match_id", "provider", "ball_id"})
    },
    indexes = {
        @Index(name = "idx_commentary_seq", columnList = "match_id, provider, sequence_num")
    }
)
public class ProviderCommentaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false, length = 64)
    private String matchId;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "ball_id", nullable = false, length = 64)
    private String ballId;

    @Column(name = "sequence_num", nullable = false)
    private long sequence;

    @Column(name = "revision_num", nullable = false)
    private int revision;

    @Column(name = "innings", nullable = false)
    private int innings;

    @Column(name = "over_num", nullable = false)
    private int over;

    @Column(name = "ball_num", nullable = false)
    private int ball;

    @Column(name = "text", nullable = false, length = 1024)
    private String text;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ProviderCommentaryEntity() {
    }

    public ProviderCommentaryEntity(String matchId, String provider, String ballId,
                                    long sequence, int revision, int innings,
                                    int over, int ball, String text, Instant occurredAt) {
        this.matchId = matchId;
        this.provider = provider;
        this.ballId = ballId;
        this.sequence = sequence;
        this.revision = revision;
        this.innings = innings;
        this.over = over;
        this.ball = ball;
        this.text = text;
        this.occurredAt = occurredAt;
        this.updatedAt = Instant.now();
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

    public String getBallId() {
        return ballId;
    }

    public void setBallId(String ballId) {
        this.ballId = ballId;
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

    public int getInnings() {
        return innings;
    }

    public void setInnings(int innings) {
        this.innings = innings;
    }

    public int getOver() {
        return over;
    }

    public void setOver(int over) {
        this.over = over;
    }

    public int getBall() {
        return ball;
    }

    public void setBall(int ball) {
        this.ball = ball;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
