package com.basiclab.iot.node.service;

import com.basiclab.iot.node.domain.vo.NodeWorkloadDeployReqVO;
import com.basiclab.iot.node.domain.vo.NodeWorkloadDeployRespVO;

public interface NodeCommandService {

    NodeWorkloadDeployRespVO deployWorkload(NodeWorkloadDeployReqVO reqVO);

    void stopWorkload(Long nodeId, String workloadType, String workloadId);

}
