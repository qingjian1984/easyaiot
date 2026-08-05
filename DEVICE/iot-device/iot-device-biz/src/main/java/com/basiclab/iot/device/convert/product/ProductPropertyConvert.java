package com.basiclab.iot.device.convert.product;

import com.basiclab.iot.device.dal.dataobject.product.ProductPropertyDO;
import com.basiclab.iot.device.domain.device.vo.ProductProperties;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 迁移期根属性 API 对象与持久化对象的显式转换边界。
 */
@Mapper
public interface ProductPropertyConvert {

    ProductPropertyConvert INSTANCE = Mappers.getMapper(ProductPropertyConvert.class);

    @Mapping(target = "tenantId", ignore = true)
    ProductPropertyDO toDO(ProductProperties source);

    ProductProperties toLegacy(ProductPropertyDO source);

    List<ProductPropertyDO> toDOList(List<ProductProperties> source);

    List<ProductProperties> toLegacyList(List<ProductPropertyDO> source);
}
