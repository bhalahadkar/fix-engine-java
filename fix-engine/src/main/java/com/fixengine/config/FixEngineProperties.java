package com.fixengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "fix")
public class FixEngineProperties {

    private NodeProperties node = new NodeProperties();
    private ClusterProperties cluster = new ClusterProperties();
    private HazelcastProperties hazelcast = new HazelcastProperties();
    private List<SessionProperties> sessions = new ArrayList<>();

    @Data
    public static class NodeProperties {
        private String id = "node-1";
        private String host = "localhost";
        private int port = 8080;
    }

    @Data
    public static class ClusterProperties {
        private ZooKeeperProperties zookeeper = new ZooKeeperProperties();

        @Data
        public static class ZooKeeperProperties {
            private String connectString = "localhost:2181";
            private int sessionTimeoutMs = 5000;
            private int connectionTimeoutMs = 3000;
            private String leaderPath = "/fix-engine/leader";
            private int retryBaseSleepMs = 1000;
            private int retryMaxAttempts = 3;
        }
    }

    @Data
    public static class HazelcastProperties {
        private String clusterName = "fix-engine-cluster";
        private List<String> members = new ArrayList<>();
        private int port = 5701;
    }

    @Data
    public static class SessionProperties {
        private String sessionId;
        private String beginString = "FIX.4.4";
        private String senderCompId;
        private String targetCompId;
        private String mode = "INITIATOR";   // INITIATOR | ACCEPTOR
        private String host;
        private int port;
        private int heartbeatInterval = 30;
        private int reconnectInterval = 10;
        private boolean resetOnLogon = false;
        private boolean resetOnLogout = false;
        private boolean resetOnDisconnect = false;
        private boolean enabled = true;
    }
}
