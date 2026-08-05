package com.basiclab.iot.device.dal.pgsql.product;

import com.basiclab.iot.device.dal.dataobject.product.ProductPropertyDO;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ProductPropertiesMapper
 *
 * @author 翱翔的雄库鲁
 * @email andywebjava@163.com
 * @wechat EasyAIoT2025
 */
@Mapper
public interface ProductPropertiesMapper {
    /**
     * 通过产品标识删除
     * @param templateIdentifications 产品标识列表
     */
    void deleteByTemplateIds(@Param("templateIdentifications") List<String> templateIdentifications);

    /**
     * delete by primary key
     *
     * @param id primaryKey
     * @return deleteCount
     */
    int deleteByPrimaryKey(Long id);

    /**
     * insert record to table
     *
     * @param record the record
     * @return insert count
     */
    int insert(ProductPropertyDO record);

    int insertOrUpdate(ProductPropertyDO record);

    int insertOrUpdateSelective(ProductPropertyDO record);

    /**
     * insert record to table selective
     *
     * @param record the record
     * @return insert count
     */
    int insertSelective(ProductPropertyDO record);

    /**
     * select by primary key
     *
     * @param id primary key
     * @return object by primary key
     */
    ProductPropertyDO selectByPrimaryKey(Long id);

    /**
     * update record selective
     *
     * @param record the updated record
     * @return update count
     */
    int updateByPrimaryKeySelective(ProductPropertyDO record);

    /**
     * update record
     *
     * @param record the updated record
     * @return update count
     */
    int updateByPrimaryKey(ProductPropertyDO record);

    int updateBatch(@Param("list") List<ProductPropertyDO> list);

    int updateBatchSelective(@Param("list") List<ProductPropertyDO> list);

    int batchInsert(@Param("list") List<ProductPropertyDO> list);

    /**
     * 查询产品模型服务属性
     *
     * @param id 产品模型服务属性主键
     * @return 产品模型服务属性
     */
    ProductPropertyDO selectProductPropertiesById(Long id);

    /**
     * 查询产品模型服务属性列表
     *
     * @param productProperties 产品模型服务属性
     * @return 产品模型服务属性集合
     */
    List<ProductPropertyDO> selectProductPropertiesList(ProductPropertyDO productProperty);

    /**
     * 新增产品模型服务属性
     *
     * @param productProperties 产品模型服务属性
     * @return 结果
     */
    int insertProductProperties(ProductPropertyDO productProperty);

    /**
     * 修改产品模型服务属性
     *
     * @param productProperties 产品模型服务属性
     * @return 结果
     */
    int updateProductProperties(ProductPropertyDO productProperty);

    /**
     * 批量删除产品模型服务属性
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    int deleteProductPropertiesByIds(Long[] ids);

    List<ProductPropertyDO> selectPropertiesByPropertiesIdList(@Param("propertiesIdList") List<Long> propertiesIdList);
}
