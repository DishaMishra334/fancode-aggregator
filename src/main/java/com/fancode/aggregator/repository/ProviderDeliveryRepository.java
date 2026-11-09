package com.fancode.aggregator.repository;

import com.fancode.aggregator.entity.ProviderDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderDeliveryRepository extends JpaRepository<ProviderDeliveryEntity, Long> {
    boolean existsByProviderAndDeliveryId(String provider, String deliveryId);
    Optional<ProviderDeliveryEntity> findByProviderAndDeliveryId(String provider, String deliveryId);
}

