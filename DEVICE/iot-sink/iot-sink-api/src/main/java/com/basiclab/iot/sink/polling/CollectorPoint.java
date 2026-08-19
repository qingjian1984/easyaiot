package com.basiclab.iot.sink.polling;

/** Immutable point definition from collector-config-snapshot-v1.1. */
public record CollectorPoint(
        String propertyCode,
        String function,
        int address,
        int quantity,
        String dataType,
        String byteOrder,
        String wordOrder,
        String scale,
        String offset,
        String dataPriority,
        boolean writable,
        String pollGroup
) {
    public CollectorPoint {
        if (propertyCode == null || propertyCode.isBlank()) {
            throw new IllegalArgumentException("propertyCode is required");
        }
        if (function == null || function.isBlank() || dataType == null || dataType.isBlank()
                || byteOrder == null || byteOrder.isBlank() || wordOrder == null || wordOrder.isBlank()
                || scale == null || scale.isBlank() || offset == null || offset.isBlank()
                || dataPriority == null || dataPriority.isBlank() || pollGroup == null || pollGroup.isBlank()) {
            throw new IllegalArgumentException("point fields are required");
        }
        if (address < 0 || quantity < 1) {
            throw new IllegalArgumentException("invalid point range");
        }
    }
}
