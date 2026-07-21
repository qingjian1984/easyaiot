package com.basiclab.iot.dataset.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 补齐大数据集查询依赖的索引，兼容当前未接入数据库迁移工具的部署方式。
 */
@Component
@Slf4j
public class DatasetSchemaInitializer implements ApplicationRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(
                    "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_dataset_image_dataset_name "
                            + "ON dataset_image (dataset_id, name)");
            jdbcTemplate.execute(
                    "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_dataset_image_dataset_completed "
                            + "ON dataset_image (dataset_id, completed)");
            log.info("[DatasetSchemaInitializer] dataset_image 大数据集索引已就绪");
        } catch (Exception e) {
            log.error("[DatasetSchemaInitializer] 创建 dataset_image 索引失败: {}", e.getMessage(), e);
        }
    }
}
