package com.basiclab.iot.node.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NodeRoleEnum {

    COMPUTE("compute", "计算节点"),
    MEDIA("media", "媒体节点"),
    HYBRID("hybrid", "混合节点");

    private final String role;
    private final String name;

}
