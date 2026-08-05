package com.basiclab.iot.device.domain.product.vo.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.math.BigDecimal;

/** 产品或模板根级属性保存请求，不接受 tenantId/serviceId。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("产品根级属性保存请求")
public class ProductRootPropertySaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("属性记录ID")
    private Long id;

    @NotBlank
    @ApiModelProperty(value = "属性标识", required = true)
    private String propertyCode;

    @NotBlank
    @ApiModelProperty(value = "属性名称", required = true)
    private String propertyName;

    @NotBlank
    @ApiModelProperty(value = "数据类型", required = true)
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
}
