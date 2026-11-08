package com.fancode.aggregator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String primaryProvider = "provider-a";
    private String canonicalMatchId = "match-1001";
    private Map<String, String> matchMappings = new HashMap<>();
    private Poller poller = new Poller();

    public AppProperties() {
        // Defaults
        matchMappings.put("a-match-1001", "match-1001");
        matchMappings.put("b-match-1001", "match-1001");
    }

    public String resolveCanonicalMatchId(String providerMatchId) {
        if (providerMatchId == null) {
            return canonicalMatchId;
        }
        return matchMappings.getOrDefault(providerMatchId, providerMatchId);
    }

    public String getPrimaryProvider() {
        return primaryProvider;
    }

    public void setPrimaryProvider(String primaryProvider) {
        this.primaryProvider = primaryProvider;
    }

    public String getCanonicalMatchId() {
        return canonicalMatchId;
    }

    public void setCanonicalMatchId(String canonicalMatchId) {
        this.canonicalMatchId = canonicalMatchId;
    }

    public Map<String, String> getMatchMappings() {
        return matchMappings;
    }

    public void setMatchMappings(Map<String, String> matchMappings) {
        this.matchMappings = matchMappings;
    }

    public Poller getPoller() {
        return poller;
    }

    public void setPoller(Poller poller) {
        this.poller = poller;
    }

    public static class Poller {
        private boolean enabled = false;
        private long intervalMs = 5000;
        private String providerBBaseUrl = "http://localhost:8080";
        private String matchId = "b-match-1001";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }

        public String getProviderBBaseUrl() {
            return providerBBaseUrl;
        }

        public void setProviderBBaseUrl(String providerBBaseUrl) {
            this.providerBBaseUrl = providerBBaseUrl;
        }

        public String getMatchId() {
            return matchId;
        }

        public void setMatchId(String matchId) {
            this.matchId = matchId;
        }
    }
}
