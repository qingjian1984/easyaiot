package com.basiclab.iot.sink.telemetry.inbox;

import java.util.List;

/**
 * TD-003 §10 中心 Inbox 接收结果（sealed）。
 */
public sealed interface InboxReceiveResult permits InboxReceiveResult.Received, InboxReceiveResult.Duplicate, InboxReceiveResult.Collision {

    record Received(List<String> messageIds) implements InboxReceiveResult {
    }

    record Duplicate(List<String> messageIds) implements InboxReceiveResult {
    }

    record Collision(List<String> messageIds) implements InboxReceiveResult {
    }
}
