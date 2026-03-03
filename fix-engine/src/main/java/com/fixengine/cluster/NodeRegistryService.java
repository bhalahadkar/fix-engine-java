package com.fixengine.cluster;

import com.fixengine.cache.SessionStateCache;
import com.fixengine.config.FixEngineProperties;
import com.fixengine.config.HazelcastConfig;
import com.fixengine.dto.NodeInfoDto;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers this node in the Hazelcast cluster registry and
 * publishes periodic heartbeats so other nodes can track cluster health.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeRegistryService {

    private final HazelcastInstance hazelcastInstance;
    private final FixEngineProperties properties;
    private final LeaderElectionService leaderElectionService;
    private final SessionStateCache sessionStateCache;

    @Scheduled(fixedDelay = 15000)
    public void publishHeartbeat() {
        try {
            IMap<String, NodeInfoDto> nodeMap = hazelcastInstance.getMap(HazelcastConfig.NODE_REGISTRY_MAP);
            NodeInfoDto info = buildNodeInfo();
            nodeMap.put(properties.getNode().getId(), info);
            log.debug("Published heartbeat for node: {}", properties.getNode().getId());
        } catch (Exception e) {
            log.error("Failed to publish node heartbeat", e);
        }
    }

    public List<NodeInfoDto> getAllNodes() {
        IMap<String, NodeInfoDto> nodeMap = hazelcastInstance.getMap(HazelcastConfig.NODE_REGISTRY_MAP);
        return new ArrayList<>(nodeMap.values());
    }

    public NodeInfoDto getThisNode() {
        return buildNodeInfo();
    }

    private NodeInfoDto buildNodeInfo() {
        OperatingSystemMXBean osMBean = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memMBean = ManagementFactory.getMemoryMXBean();

        double cpuLoad = -1;
        if (osMBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            cpuLoad = sunOs.getCpuLoad() * 100;
        }

        long usedHeap = memMBean.getHeapMemoryUsage().getUsed();
        long maxHeap  = memMBean.getHeapMemoryUsage().getMax();
        double memPct = maxHeap > 0 ? (double) usedHeap / maxHeap * 100 : 0;

        NodeInfoDto dto = new NodeInfoDto();
        dto.setNodeId(properties.getNode().getId());
        dto.setHost(properties.getNode().getHost());
        dto.setPort(properties.getNode().getPort());
        dto.setLeader(leaderElectionService.isLeader());
        dto.setStatus(leaderElectionService.isLeader() ? "ACTIVE" : "STANDBY");
        dto.setCpuPercent(cpuLoad >= 0 ? Math.round(cpuLoad * 10) / 10.0 : 0);
        dto.setMemPercent(Math.round(memPct * 10) / 10.0);
        dto.setActiveSessions(sessionStateCache.getConnectedSessionCount());
        dto.setLastHeartbeat(Instant.now());

        return dto;
    }
}
