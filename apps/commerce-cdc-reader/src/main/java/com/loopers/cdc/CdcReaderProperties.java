package com.loopers.cdc;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "cdc.reader")
public class CdcReaderProperties {

    private String mysqlHost = "localhost";
    private int mysqlPort = 3307;
    private String username = "root";
    private String password = "root";
    private long serverId = 223346L;
    private String binlogFilename;
    private long binlogPosition = 4L;

    private String topicPrefix = "cdc-app";
    private List<String> includeDatabases = new ArrayList<>();
    private List<String> includeTables = new ArrayList<>();
    /** Kafka 전송 완료 대기 (미확인 전송 시 binlog 진행으로 유실 방지) */
    private Duration sendTimeout = Duration.ofSeconds(30);

    public String getMysqlHost() {
        return mysqlHost;
    }

    public void setMysqlHost(String mysqlHost) {
        this.mysqlHost = mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public void setMysqlPort(int mysqlPort) {
        this.mysqlPort = mysqlPort;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

    public String getBinlogFilename() {
        return binlogFilename;
    }

    public void setBinlogFilename(String binlogFilename) {
        this.binlogFilename = binlogFilename;
    }

    public long getBinlogPosition() {
        return binlogPosition;
    }

    public void setBinlogPosition(long binlogPosition) {
        this.binlogPosition = binlogPosition;
    }

    public String getTopicPrefix() {
        return topicPrefix;
    }

    public void setTopicPrefix(String topicPrefix) {
        this.topicPrefix = topicPrefix;
    }

    public List<String> getIncludeDatabases() {
        return includeDatabases;
    }

    public void setIncludeDatabases(List<String> includeDatabases) {
        this.includeDatabases = includeDatabases;
    }

    public List<String> getIncludeTables() {
        return includeTables;
    }

    public void setIncludeTables(List<String> includeTables) {
        this.includeTables = includeTables;
    }

    public Duration getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(Duration sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public boolean shouldInclude(String db, String table) {
        if (db == null || table == null) {
            return false;
        }
        if (!includeDatabases.isEmpty() && !includeDatabases.contains(db)) {
            return false;
        }
        if (!includeTables.isEmpty() && !includeTables.contains(db + "." + table)) {
            return false;
        }
        return true;
    }
}
