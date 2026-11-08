package com.fancode.aggregator.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "matches")
public class MatchEntity {

    @Id
    @Column(name = "match_id", nullable = false, length = 64)
    private String matchId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "scheduled";

    @Column(name = "active_provider", length = 32)
    private String activeProvider;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public MatchEntity() {
    }

    public MatchEntity(String matchId, String status) {
        this.matchId = matchId;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getMatchId() {
        return matchId;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getActiveProvider() {
        return activeProvider;
    }

    public void setActiveProvider(String activeProvider) {
        this.activeProvider = activeProvider;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
