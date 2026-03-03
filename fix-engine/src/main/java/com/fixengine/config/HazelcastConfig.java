package com.fixengine.config;

import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class HazelcastConfig {

    private final FixEngineProperties properties;

    public static final String SESSION_STATE_MAP    = "fix-session-state";
    public static final String SESSION_SEQ_MAP      = "fix-session-sequences";
    public static final String NODE_REGISTRY_MAP    = "fix-node-registry";
    public static final String MESSAGE_STATS_MAP    = "fix-message-stats";

    @Bean
    public Config hazelcastConfig() {
        Config config = new Config();
        config.setClusterName(properties.getHazelcast().getClusterName());
        config.setInstanceName("fix-engine-" + properties.getNode().getId());

        // Network
        NetworkConfig network = config.getNetworkConfig();
        network.setPort(properties.getHazelcast().getPort());
        network.setPortAutoIncrement(false);

        JoinConfig join = network.getJoin();
        join.getMulticastConfig().setEnabled(false);

        TcpIpConfig tcpIp = join.getTcpIpConfig();
        tcpIp.setEnabled(true);
        List<String> members = properties.getHazelcast().getMembers();
        if (members != null && !members.isEmpty()) {
            members.forEach(tcpIp::addMember);
            log.info("Hazelcast cluster members: {}", members);
        } else {
            tcpIp.addMember("127.0.0.1");
        }

        // Session State Map — replicated, no expiry, 2 backups for 3-node cluster
        MapConfig sessionStateMap = new MapConfig(SESSION_STATE_MAP);
        sessionStateMap.setBackupCount(2);
        sessionStateMap.setAsyncBackupCount(0);
        sessionStateMap.setTimeToLiveSeconds(0);
        sessionStateMap.setMaxIdleSeconds(0);
        sessionStateMap.setEvictionConfig(
                new EvictionConfig().setEvictionPolicy(EvictionPolicy.NONE)
        );
        config.addMapConfig(sessionStateMap);

        // Sequence Numbers Map
        MapConfig seqMap = new MapConfig(SESSION_SEQ_MAP);
        seqMap.setBackupCount(2);
        seqMap.setTimeToLiveSeconds(0);
        config.addMapConfig(seqMap);

        // Node Registry Map
        MapConfig nodeMap = new MapConfig(NODE_REGISTRY_MAP);
        nodeMap.setBackupCount(2);
        nodeMap.setTimeToLiveSeconds(60); // Nodes refresh every 30s
        config.addMapConfig(nodeMap);

        // Message Stats Map
        MapConfig statsMap = new MapConfig(MESSAGE_STATS_MAP);
        statsMap.setBackupCount(1);
        statsMap.setTimeToLiveSeconds(3600); // 1 hour rolling stats
        config.addMapConfig(statsMap);

        return config;
    }

    @Bean
    public HazelcastInstance hazelcastInstance(Config hazelcastConfig) {
        HazelcastInstance instance = Hazelcast.newHazelcastInstance(hazelcastConfig);
        log.info("Hazelcast instance started: {}", instance.getName());
        return instance;
    }
}
