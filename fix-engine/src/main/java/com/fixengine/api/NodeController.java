package com.fixengine.api;

import com.fixengine.cluster.LeaderElectionService;
import com.fixengine.cluster.NodeRegistryService;
import com.fixengine.dto.ApiResponse;
import com.fixengine.dto.NodeInfoDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/nodes")
@RequiredArgsConstructor
@Tag(name = "Cluster Nodes", description = "Monitor cluster node health and leadership")
public class NodeController {

    private final NodeRegistryService nodeRegistryService;
    private final LeaderElectionService leaderElectionService;

    @GetMapping
    @Operation(summary = "List all cluster nodes with health metrics")
    public ResponseEntity<ApiResponse<List<NodeInfoDto>>> listNodes() {
        return ResponseEntity.ok(ApiResponse.ok(nodeRegistryService.getAllNodes()));
    }

    @GetMapping("/current")
    @Operation(summary = "Get info for this node")
    public ResponseEntity<ApiResponse<NodeInfoDto>> currentNode() {
        return ResponseEntity.ok(ApiResponse.ok(nodeRegistryService.getThisNode()));
    }

    @GetMapping("/leader")
    @Operation(summary = "Get current leader node ID")
    public ResponseEntity<ApiResponse<String>> getLeader() {
        return ResponseEntity.ok(ApiResponse.ok(leaderElectionService.getLeaderId()));
    }
}
