package com.basiclab.iot.device.dal.dataobject.product;

import com.basiclab.iot.common.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 产品或模板根级属性持久化对象。
 *
 * <p>服务输入/输出参数不属于本对象，只能持久化到 command request/response 链。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProductPropertyDO extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String propertyName;
    private String propertyCode;
    private String datatype;
    private String description;
    private String enumlist;
    private String max;
    private Long maxlength;
    private String method;
    private String min;
    private Integer required;
    private Integer step;
    private String unit;
    private String templateIdentification;
    private String productIdentification;
    private Long tenantId;
}
