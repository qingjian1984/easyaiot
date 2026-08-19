package com.basiclab.iot.node.service.collector.config;

import java.util.List;
import java.util.Optional;

/**
 * iot-device collector release 内部 API 的最小 port。
 *
 * <p>生产适配器必须使用已接受的内部服务身份和 {@code CollectorConfigReleaseInternalApi} 合同；
 * 派发器本身不拥有数据库、租户或 release 表访问权。测试使用 fake 实现。</p>
 */
public interface CollectorConfigReleaseClientPort {

    List<CollectorConfigReleasePending> listPending(int limit);

    Optional<CollectorConfigReleaseDetail> getDetail(String releaseId);

    CollectorConfigObservedResponse reportObserved(CollectorConfigReleaseObservedReport report);
}
