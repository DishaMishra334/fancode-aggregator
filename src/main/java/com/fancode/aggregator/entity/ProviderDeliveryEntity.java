package com.fancode.aggregator.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "provider_deliveries",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_provider_delivery", columnNames = {"provider", "delivery_id"})
    }
)
public class ProviderDeliveryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "delivery_id", nullable = false, length = 64)
    private String deliveryId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    public ProviderDeliveryEntity() {
    }

    public ProviderDeliveryEntity(String provider, String deliveryId) {
        this.provider = provider;
        this.deliveryId = deliveryId;
        this.receivedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
