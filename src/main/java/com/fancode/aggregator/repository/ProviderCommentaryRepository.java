package com.fancode.aggregator.repository;

import com.fancode.aggregator.entity.ProviderCommentaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderCommentaryRepository extends JpaRepository<ProviderCommentaryEntity, Long> {
    Optional<ProviderCommentaryEntity> findByMatchIdAndProviderAndBallId(String matchId, String provider, String ballId);
    List<ProviderCommentaryEntity> findByMatchIdAndProviderOrderBySequenceAsc(String matchId, String provider);
}

