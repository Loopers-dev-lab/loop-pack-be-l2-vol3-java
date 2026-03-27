package com.loopers.collector.metrics;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "collector.metrics.lag")
public class CollectorLagProperties {

    private boolean enabled = true;

    private long pollIntervalMs = 60_000L;

    private List<GroupTopics> groups = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public List<GroupTopics> getGroups() {
        return groups;
    }

    public void setGroups(List<GroupTopics> groups) {
        this.groups = groups;
    }

    public static final class GroupTopics {
        private String groupId;
        private List<String> topics = new ArrayList<>();

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public List<String> getTopics() {
            return topics;
        }

        public void setTopics(List<String> topics) {
            this.topics = topics;
        }
    }
}
