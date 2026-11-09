package com.fancode.aggregator.config;

import com.fancode.aggregator.entity.MatchEntity;
import com.fancode.aggregator.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner initDatabase(MatchRepository matchRepository, AppProperties appProperties) {
        return args -> {
            String defaultMatchId = appProperties.getCanonicalMatchId();
            if (!matchRepository.existsById(defaultMatchId)) {
                MatchEntity initialMatch = new MatchEntity(defaultMatchId, "scheduled");
                matchRepository.save(initialMatch);
                log.info("Initialized default canonical match: [{}] with status 'scheduled'", defaultMatchId);
            }
        };
    }
}
