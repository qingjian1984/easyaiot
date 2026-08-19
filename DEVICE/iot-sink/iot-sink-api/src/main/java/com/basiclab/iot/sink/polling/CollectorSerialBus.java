package com.basiclab.iot.sink.polling;

import java.util.List;

/** Immutable serial bus definition from collector-config-snapshot-v1.1. */
public record CollectorSerialBus(
        String busId,
        String serialPort,
        int baudRate,
        int dataBits,
        String stopBits,
        String parity,
        int transmitDelayMs,
        boolean rs485Mode,
        List<CollectorDevice> devices
) {
    public CollectorSerialBus {
        if (busId == null || busId.isBlank() || serialPort == null || serialPort.isBlank()
                || baudRate < 1 || dataBits < 5 || dataBits > 8 || stopBits == null || stopBits.isBlank()
                || parity == null || parity.isBlank() || transmitDelayMs < 0 || devices == null || devices.isEmpty()) {
            throw new IllegalArgumentException("invalid collector serial bus");
        }
        devices = List.copyOf(devices);
    }
}
