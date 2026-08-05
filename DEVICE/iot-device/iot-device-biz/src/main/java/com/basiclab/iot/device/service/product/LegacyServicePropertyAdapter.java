package com.basiclab.iot.device.service.product;

import com.basiclab.iot.device.domain.device.vo.ProductServiceDetailVO;
import com.basiclab.iot.device.domain.device.vo.ProductServiceParamVO;
import com.basiclab.iot.device.domain.product.vo.result.ProductPropertyResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 旧“按服务查询属性”契约的只读兼容适配器。
 *
 * <p>数据只从 command request/response 链投影，不访问 product_properties。</p>
 */
@Service
@RequiredArgsConstructor
public class LegacyServicePropertyAdapter {

    private final ProductServiceThingModelHelper productServiceThingModelHelper;

    public List<ProductPropertyResultVO> findAllByServiceId(Long serviceId) {
        if (serviceId == null) {
            return Collections.emptyList();
        }
        ProductServiceDetailVO detail = productServiceThingModelHelper.getDetail(serviceId);
        if (detail == null) {
            return Collections.emptyList();
        }
        List<ProductPropertyResultVO> result = new ArrayList<>();
        append(result, serviceId, detail.getInputParams());
        append(result, serviceId, detail.getOutParams());
        return result;
    }

    private void append(List<ProductPropertyResultVO> target, Long serviceId,
                        List<ProductServiceParamVO> params) {
        if (params == null) {
            return;
        }
        for (ProductServiceParamVO param : params) {
            target.add(ProductPropertyResultVO.builder()
                    .id(param.getId())
                    .serviceId(serviceId)
                    .propertyCode(firstNonBlank(param.getPropertyCode(), param.getParameterCode()))
                    .propertyName(firstNonBlank(param.getPropertyName(), param.getParameterName()))
                    .datatype(param.getDatatype())
                    .description(firstNonBlank(param.getDescription(), param.getParameterDescription()))
                    .enumlist(param.getEnumlist())
                    .max(asString(param.getMax()))
                    .maxlength(asString(param.getMaxlength()))
                    .min(asString(param.getMin()))
                    .required(asString(param.getRequired()))
                    .step(asString(param.getStep()))
                    .unit(param.getUnit())
                    .build());
        }
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first : second;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
