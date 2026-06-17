package com.basiclab.iot.node.service;

import com.basiclab.iot.node.domain.vo.NodeMediaRemoteDeployRespVO;
import com.basiclab.iot.node.domain.vo.NodeStorageMountCheckRespVO;
import com.basiclab.iot.node.domain.vo.NodeStorageStackCheckRespVO;

public interface NodeStorageService {

    NodeStorageStackCheckRespVO checkStorageStackBySsh(Long nodeId);

    NodeStorageMountCheckRespVO checkStorageMountBySsh(Long nodeId);

    NodeMediaRemoteDeployRespVO deployStorageOsdBySsh(Long nodeId);

    NodeMediaRemoteDeployRespVO deployStorageClientBySsh(Long nodeId);

    NodeMediaRemoteDeployRespVO deployStoragePoolBySsh(Long nodeId);

    NodeMediaRemoteDeployRespVO stopStorageOsdBySsh(Long nodeId);

    NodeMediaRemoteDeployRespVO unmountStorageBySsh(Long nodeId);

}
