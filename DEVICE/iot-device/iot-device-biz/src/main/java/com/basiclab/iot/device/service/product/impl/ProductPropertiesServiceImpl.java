package com.basiclab.iot.device.service.product.impl;

import com.basiclab.iot.device.dal.pgsql.product.ProductPropertiesMapper;
import com.basiclab.iot.device.convert.product.ProductPropertyConvert;
import com.basiclab.iot.device.dal.dataobject.product.ProductPropertyDO;
import com.basiclab.iot.device.domain.device.vo.ProductProperties;
import com.basiclab.iot.device.service.product.ProductPropertiesService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * ProductPropertiesServiceImpl
 *
 * @author 翱翔的雄库鲁
 * @email andywebjava@163.com
 * @wechat EasyAIoT2025
 */
@Service
public class ProductPropertiesServiceImpl implements ProductPropertiesService {

    @Resource
    private ProductPropertiesMapper productPropertiesMapper;

    @Override
    public void deleteByTemplateIds(List<String> templateIdentifications) {
        productPropertiesMapper.deleteByTemplateIds(templateIdentifications);
    }

    @Override
    public int deleteByPrimaryKey(Long id) {
        return productPropertiesMapper.deleteByPrimaryKey(id);
    }

    @Override
    public int insert(ProductProperties record) {
        ProductPropertyDO entity = ProductPropertyConvert.INSTANCE.toDO(record);
        int count = productPropertiesMapper.insert(entity);
        copyGeneratedId(record, entity);
        return count;
    }

    @Override
    public int insertOrUpdate(ProductProperties record) {
        ProductPropertyDO entity = ProductPropertyConvert.INSTANCE.toDO(record);
        int count = productPropertiesMapper.insertOrUpdate(entity);
        copyGeneratedId(record, entity);
        return count;
    }

    @Override
    public int insertOrUpdateSelective(ProductProperties record) {
        ProductPropertyDO entity = ProductPropertyConvert.INSTANCE.toDO(record);
        int count = productPropertiesMapper.insertOrUpdateSelective(entity);
        copyGeneratedId(record, entity);
        return count;
    }

    @Override
    public int insertSelective(ProductProperties record) {
        ProductPropertyDO entity = ProductPropertyConvert.INSTANCE.toDO(record);
        int count = productPropertiesMapper.insertSelective(entity);
        copyGeneratedId(record, entity);
        return count;
    }

    @Override
    public ProductProperties selectByPrimaryKey(Long id) {
        return ProductPropertyConvert.INSTANCE.toLegacy(productPropertiesMapper.selectByPrimaryKey(id));
    }

    @Override
    public int updateByPrimaryKeySelective(ProductProperties record) {
        return productPropertiesMapper.updateByPrimaryKeySelective(ProductPropertyConvert.INSTANCE.toDO(record));
    }

    @Override
    public int updateByPrimaryKey(ProductProperties record) {
        return productPropertiesMapper.updateByPrimaryKey(ProductPropertyConvert.INSTANCE.toDO(record));
    }

    @Override
    public int updateBatch(List<ProductProperties> list) {
        return productPropertiesMapper.updateBatch(ProductPropertyConvert.INSTANCE.toDOList(list));
    }

    @Override
    public int updateBatchSelective(List<ProductProperties> list) {
        return productPropertiesMapper.updateBatchSelective(ProductPropertyConvert.INSTANCE.toDOList(list));
    }

    @Override
    public int batchInsert(List<ProductProperties> list) {
        List<ProductPropertyDO> entities = ProductPropertyConvert.INSTANCE.toDOList(list);
        int count = productPropertiesMapper.batchInsert(entities);
        for (int i = 0; i < list.size() && i < entities.size(); i++) {
            copyGeneratedId(list.get(i), entities.get(i));
        }
        return count;
    }

    /**
     * 查询产品模型服务属性
     *
     * @param id 产品模型服务属性主键
     * @return 产品模型服务属性
     */
    @Override
    public ProductProperties selectProductPropertiesById(Long id) {
        return ProductPropertyConvert.INSTANCE.toLegacy(productPropertiesMapper.selectProductPropertiesById(id));
    }

    /**
     * 查询产品模型服务属性列表
     *
     * @param productProperties 产品模型服务属性
     * @return 产品模型服务属性
     */
    @Override
    public List<ProductProperties> selectProductPropertiesList(ProductProperties productProperties) {
        return ProductPropertyConvert.INSTANCE.toLegacyList(productPropertiesMapper.selectProductPropertiesList(
                ProductPropertyConvert.INSTANCE.toDO(productProperties)));
    }

    /**
     * 新增产品模型服务属性
     *
     * @param productProperties 产品模型服务属性
     * @return 结果
     */
    @Override
    public int insertProductProperties(ProductProperties productProperties) {
        ProductPropertyDO entity = ProductPropertyConvert.INSTANCE.toDO(productProperties);
        int count = productPropertiesMapper.insertProductProperties(entity);
        copyGeneratedId(productProperties, entity);
        return count;
    }

    /**
     * 修改产品模型服务属性
     *
     * @param productProperties 产品模型服务属性
     * @return 结果
     */
    @Override
    public int updateProductProperties(ProductProperties productProperties) {
        return productPropertiesMapper.updateProductProperties(ProductPropertyConvert.INSTANCE.toDO(productProperties));
    }

    /**
     * 批量删除产品模型服务属性
     *
     * @param ids 需要删除的产品模型服务属性主键
     * @return 结果
     */
    @Override
    public int deleteProductPropertiesByIds(Long[] ids) {
        return productPropertiesMapper.deleteProductPropertiesByIds(ids);
    }

    @Override
    public List<ProductProperties> selectPropertiesByPropertiesIdList(List<Long> propertiesIdList) {
        return ProductPropertyConvert.INSTANCE.toLegacyList(
                productPropertiesMapper.selectPropertiesByPropertiesIdList(propertiesIdList));
    }

    private void copyGeneratedId(ProductProperties target, ProductPropertyDO source) {
        if (target != null && source != null) {
            target.setId(source.getId());
        }
    }
}
