package com.basiclab.iot.node.controller;

import com.basiclab.iot.common.domain.CommonResult;
import com.basiclab.iot.node.domain.vo.NodeMediaRemoteDeployRespVO;
import com.basiclab.iot.node.domain.vo.NodeStorageMountCheckRespVO;
import com.basiclab.iot.node.domain.vo.NodeStorageStackCheckRespVO;
import com.basiclab.iot.node.service.NodeStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import static com.basiclab.iot.common.domain.CommonResult.success;

@Tag(name = "存储 - Ceph 集群纳管")
@RestController
@RequestMapping("/node/storage/")
@Validated
@Slf4j
public class NodeStorageController {

    @Resource
    private NodeStorageService nodeStorageService;

    @PostMapping("/check-ssh")
    @Operation(summary = "通过 SSH 检测 Ceph 存储节点集群与挂载状态")
    public CommonResult<NodeStorageStackCheckRespVO> checkBySsh(@RequestParam("nodeId") Long nodeId) {
        return success(nodeStorageService.checkStorageStackBySsh(nodeId));
    }

    @PostMapping("/check-mount-ssh")
    @Operation(summary = "通过 SSH 检测 CephFS 客户端挂载状态")
    public CommonResult<NodeStorageMountCheckRespVO> checkMountBySsh(@RequestParam("nodeId") Long nodeId) {
        return success(nodeStorageService.checkStorageMountBySsh(nodeId));
    }

    @PostMapping("/deploy-osd-ssh")
    @Operation(summary = "通过 SSH 在存储节点准备 Ceph OSD")
    public CommonResult<NodeMediaRemoteDeployRespVO> deployOsdBySsh(@RequestParam("nodeId") Long nodeId) {
        return success(nodeStorageService.deployStorageOsdBySsh(nodeId));
    }

    @PostMapping("/deploy-client-ssh")
    @Operation(summary = "通过 SSH 在目标节点挂载 CephFS 客户端")
    public CommonResult<NodeMediaRemoteDeployRespVO> deployClientBySsh(@RequestParam("nodeId") Long nodeId) {
        return success(nodeStorageService.deployStorageClientBySsh(nodeId));
    }

    @PostMapping("/deploy-pool-ssh")
    @Operation(summary = "通过 SSH 在 MON 节点创建 Ceph 存储池与 CephFS")
    public CommonResult<NodeMediaRemoteDeployRespVO> deployPoolBySsh(@RequestParam("nodeId") Long nodeId) {
        return success(nodeStorageService.deployStoragePoolBySsh(nodeId));
    }

    @PostMapping("/stop-osd-ssh")
    @Operation(summary = "通过 SSH 停止存储节点 OSD 服务")
    public CommonResult<NodeMediaRemoteDeployRespVO> stopOsdBySsh(@RequestParam("nodeId") Long nodeId) {
        return success(nodeStorageService.stopStorageOsdBySsh(nodeId));
    }

    @PostMapping("/unmount-ssh")
    @Operation(summary = "通过 SSH 卸载目标节点 CephFS 挂载")
    public CommonResult<NodeMediaRemoteDeployRespVO> unmountBySsh(@RequestParam("nodeId") Long nodeId) {
        return success(nodeStorageService.unmountStorageBySsh(nodeId));
    }

}
