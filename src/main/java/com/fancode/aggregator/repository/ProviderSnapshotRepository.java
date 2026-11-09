package com.fancode.aggregator.repository;

import com.fancode.aggregator.entity.ProviderSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderSnapshotRepository extends JpaRepository<ProviderSnapshotEntity, Long> {
    Optional<ProviderSnapshotEntity> findByMatchIdAndProvider(String matchId, String provider);
    List<ProviderSnapshotEntity> findByMatchId(String matchId);
}

