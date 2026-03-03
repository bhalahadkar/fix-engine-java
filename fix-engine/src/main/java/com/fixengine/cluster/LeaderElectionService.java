package com.fixengine.cluster;

import com.fixengine.config.FixEngineProperties;
import com.fixengine.service.FixSessionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages leader election via Apache ZooKeeper + Curator.
 * The elected leader node activates all FIX sessions.
 * On leader loss, the node enters standby and the next node takes over.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderElectionService {

    private final FixEngineProperties properties;
    private final FixSessionService sessionService;

    private CuratorFramework curator;
    private LeaderLatch leaderLatch;
    private final AtomicBoolean isLeader = new AtomicBoolean(false);

    @PostConstruct
    public void start() throws Exception {
        FixEngineProperties.ClusterProperties.ZooKeeperProperties zk =
                properties.getCluster().getZookeeper();

        curator = CuratorFrameworkFactory.builder()
                .connectString(zk.getConnectString())
                .sessionTimeoutMs(zk.getSessionTimeoutMs())
                .connectionTimeoutMs(zk.getConnectionTimeoutMs())
                .retryPolicy(new ExponentialBackoffRetry(zk.getRetryBaseSleepMs(), zk.getRetryMaxAttempts()))
                .namespace("fix-engine")
                .build();

        curator.start();
        log.info("ZooKeeper curator started, connecting to: {}", zk.getConnectString());

        // Wait for connection
        boolean connected = curator.blockUntilConnected(zk.getConnectionTimeoutMs(), TimeUnit.MILLISECONDS);
        if (!connected) {
            log.warn("Could not connect to ZooKeeper within timeout — running in standalone mode");
            // Standalone fallback: become leader immediately
            becomeLeader();
            return;
        }

        leaderLatch = new LeaderLatch(curator, zk.getLeaderPath(), properties.getNode().getId());
        leaderLatch.addListener(new LeaderLatchListener() {
            @Override
            public void isLeader() {
                log.info("★ NODE {} IS NOW THE LEADER ★", properties.getNode().getId());
                isLeader.set(true);
                becomeLeader();
            }

            @Override
            public void notLeader() {
                log.info("Node {} lost leadership — entering STANDBY", properties.getNode().getId());
                isLeader.set(false);
                enterStandby();
            }
        });

        leaderLatch.start();
        log.info("Leader election started. Participating in: {}", zk.getLeaderPath());
    }

    @PreDestroy
    public void stop() {
        try {
            if (leaderLatch != null) leaderLatch.close();
            if (curator != null) curator.close();
            log.info("LeaderElectionService stopped");
        } catch (Exception e) {
            log.error("Error stopping leader election", e);
        }
    }

    public boolean isLeader() {
        return isLeader.get();
    }

    public String getLeaderId() {
        if (leaderLatch == null) return properties.getNode().getId();
        try {
            return leaderLatch.getLeader().getId();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private void becomeLeader() {
        log.info("Activating FIX engine on this node (restoring from distributed cache)...");
        sessionService.restoreAndActivateSessions();
    }

    private void enterStandby() {
        log.info("Entering STANDBY — FIX sessions will be managed by the leader node");
        sessionService.enterStandbyMode();
    }
}
