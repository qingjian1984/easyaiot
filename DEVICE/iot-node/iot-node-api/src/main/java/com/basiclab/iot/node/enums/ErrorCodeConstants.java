package com.basiclab.iot.node.enums;

import com.basiclab.iot.common.exception.ErrorCode;

/**
 * Node 模块错误码，使用 1-005-000-000 段
 */
public interface ErrorCodeConstants {

    ErrorCode COMPUTE_NODE_NOT_EXISTS = new ErrorCode(1_005_000_000, "服务器节点不存在");
    ErrorCode COMPUTE_NODE_HOST_EXISTS = new ErrorCode(1_005_000_001, "该主机地址已存在");
    ErrorCode COMPUTE_NODE_HAS_WORKLOAD = new ErrorCode(1_005_000_002, "节点上仍有运行中的工作负载，无法删除");
    ErrorCode COMPUTE_NODE_OFFLINE = new ErrorCode(1_005_000_003, "服务器节点离线");
    ErrorCode COMPUTE_NODE_NOT_PENDING = new ErrorCode(1_005_000_004, "节点已完成纳管或不在待纳管状态");
    ErrorCode SSH_CREDENTIAL_NOT_EXISTS = new ErrorCode(1_005_001_000, "SSH 凭据不存在");
    ErrorCode SSH_CONNECT_FAILED = new ErrorCode(1_005_001_001, "SSH 连接失败");
    ErrorCode AGENT_TOKEN_INVALID = new ErrorCode(1_005_002_000, "Agent 认证令牌无效");
    ErrorCode NODE_POOL_EXHAUSTED = new ErrorCode(1_005_003_000, "无可用节点，节点池已耗尽");
    ErrorCode MEDIA_BINDING_NOT_EXISTS = new ErrorCode(1_005_004_000, "设备媒体绑定不存在");
    ErrorCode MEDIA_DEPLOY_SSH_FAILED = new ErrorCode(1_005_004_001, "媒体栈 SSH 部署失败");
    ErrorCode MEDIA_CLUSTER_SOURCE_NOT_FOUND = new ErrorCode(1_005_004_002, "控制面未找到 media-cluster 源目录");
    ErrorCode AGENT_SOURCE_NOT_FOUND = new ErrorCode(1_005_005_000, "控制面未找到 Agent 源目录");

}
