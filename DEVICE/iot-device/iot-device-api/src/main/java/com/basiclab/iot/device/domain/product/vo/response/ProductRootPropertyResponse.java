package com.basiclab.iot.device.domain.product.vo.response;

import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 产品或模板根级属性响应；服务参数使用独立的服务详情响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("产品根级属性响应")
public class ProductRootPropertyResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String propertyCode;
    private String propertyName;
    private String datatype;
    private String description;
    private String enumlist;
    private BigDecimal max;
    private Long maxlength;
    private String method;
    private BigDecimal min;
    private Integer required;
    private Integer step;
    private String unit;
    private String templateIdentification;
    private String productIdentification;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
