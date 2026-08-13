package com.basiclab.iot.sink.telemetry.inbox;

import java.util.List;

/**
 * TD-003 §10 中心 Inbox 端口：接收上行 envelopes，两层幂等（message_id UNIQUE + content_sha256）。
 */
public interface TelemetryInboxPort {

    /**
     * 接收一批 envelopes，写入 Inbox（两层幂等：同 messageId 同 hash → DUPLICATE；不同 hash → COLLISION）。
     *
     * @param envelopes 已解析的 ClaimedEnvelope（含 canonical bytes）
     * @return 接收结果（Received/Duplicate/Collision per messageId）
     */
    InboxReceiveResult receiveEnvelopes(List<InboxEnvelope> envelopes);
}
