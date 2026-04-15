# 人/车大数据 Flink + Doris 四层宽表架构（人脸与车牌完全分离·可落地版）

> 版本：v3.0  
> 适用范围：**人脸**（人脸检测、人脸特征）与 **车牌**（车辆检测、车牌识别）独立处理  
> 技术栈：Apache Flink 1.17+ · Apache Doris 2.0+ · Apache Kafka 3.x  
> 设计原则：**人脸归人脸，车牌归车牌，仅通过摄像头 ID 关联，不做跨目标类型关联**

---

## 文档导引

本方案面向 **VIDEO 侧产生的结构化事件**，针对 **人脸** 与 **车牌** 两类数据分别构建独立的四层数据管道。所有字段均来自算法可输出的实际内容，无任何不切实际的假设。架构特点：

- **人脸与车牌完全分离**：各自拥有完整的 ODS → DWD → DWS → ADS 四层表。
- **每层一张宽表**：每张表字段铺平，无需跨表 Join 即可满足绝大部分查询。
- **跨摄像头串联**：基于 `global_face_id` / `global_plate_id` 以及设备拓扑关系实现。
- **查询时动态聚合**：同一摄像头下多人脸或多车牌的业务告警，直接通过 SQL 聚合实现，无需额外建表。

---

## 1. 总体数据流

```
┌─────────────────────────────────────────┐
│           VIDEO 算法侧                    │
│  - 人脸检测 / 人脸特征                     │
│  - 车牌检测 / 车牌识别                     │
└──────────────┬──────────────────────────┘
               │ Kafka (两个 Topic)
               ▼
┌─────────────────────────────────────────┐
│ Flink Job : ods_sanity (可选)            │
└──────────────┬──────────────────────────┘
               │ Routine Load / Doris Connector
               ▼
┌─────────────────────┐  ┌─────────────────────┐
│  ODS 人脸表          │  │  ODS 车牌表          │
│  ods_face_event     │  │  ods_plate_event    │
└──────────┬──────────┘  └──────────┬──────────┘
           │                        │
           ▼                        ▼
┌─────────────────────┐  ┌─────────────────────┐
│ Flink Job :          │  │ Flink Job :          │
│ dwd_face_attribution │  │ dwd_plate_attribution│
└──────────┬──────────┘  └──────────┬──────────┘
           │                        │
           ▼                        ▼
┌─────────────────────┐  ┌─────────────────────┐
│ DWD 人脸明细表        │  │ DWD 车牌明细表        │
│ dwd_face_detail     │  │ dwd_plate_detail    │
└──────────┬──────────┘  └──────────┬──────────┘
           │                        │
           ▼                        ▼
┌─────────────────────┐  ┌─────────────────────┐
│ Flink Job :          │  │ Flink Job :          │
│ dws_face_session     │  │ dws_plate_session    │
└──────────┬──────────┘  └──────────┬──────────┘
           │                        │
           ▼                        ▼
┌─────────────────────┐  ┌─────────────────────┐
│ DWS 人脸轨迹小时表    │  │ DWS 车牌轨迹小时表    │
│ dws_face_trace_1h   │  │ dws_plate_trace_1h  │
└──────────┬──────────┘  └──────────┬──────────┘
           │                        │
           ▼                        ▼
┌─────────────────────┐  ┌─────────────────────┐
│ ADS 人脸应用表        │  │ ADS 车牌应用表        │
│ ads_face_app        │  │ ads_plate_app       │
└─────────────────────┘  └─────────────────────┘
           │                        │
           └──────────┬─────────────┘
                      ▼
              大屏 / API / 告警
```

---

## 2. 数据契约与 Kafka Topic 定义

### 2.1 Kafka Topic 规划

| Topic 名称 | 内容 | 消息格式 |
|-----------|------|---------|
| `ods.face.raw` | 人脸检测、人脸特征事件 | JSON（字段铺平） |
| `ods.plate.raw` | 车辆检测、车牌识别事件 | JSON（字段铺平） |

### 2.2 人脸事件 Schema（供 VIDEO 侧对齐）

#### 2.2.1 人脸检测事件（face_detection）

```json
{
  "event_id": "uuid-face-001",
  "event_type": "face_detection",
  "device_id": "cam_01",
  "ts": 1734256800123,
  "track_id": "face_track_001",
  "bbox_x": 200,
  "bbox_y": 150,
  "bbox_w": 60,
  "bbox_h": 80,
  "score": 0.95,
  "face_gender": "male",
  "face_age": 30,
  "face_glasses": false,
  "face_mask": false,
  "face_quality": 0.88
}
```

#### 2.2.2 人脸特征事件（face_feature）

```json
{
  "event_id": "uuid-face-002",
  "event_type": "face_feature",
  "device_id": "cam_01",
  "ts": 1734256800456,
  "track_id": "face_track_001",
  "feature_id": "feat_abc123",
  "feature_score": 0.92,
  "feature_version": "v2"
}
```

### 2.3 车牌事件 Schema

#### 2.3.1 车辆检测事件（vehicle_detection）

```json
{
  "event_id": "uuid-plate-001",
  "event_type": "vehicle_detection",
  "device_id": "cam_01",
  "ts": 1734256800234,
  "track_id": "car_track_001",
  "bbox_x": 300,
  "bbox_y": 200,
  "bbox_w": 150,
  "bbox_h": 120,
  "score": 0.96,
  "vehicle_type": "car",
  "vehicle_color": "red",
  "vehicle_brand": "Toyota"
}
```

#### 2.3.2 车牌识别事件（plate_ocr）

```json
{
  "event_id": "uuid-plate-002",
  "event_type": "plate_ocr",
  "device_id": "cam_01",
  "ts": 1734256800345,
  "track_id": "car_track_001",
  "plate_no": "沪A12345",
  "plate_score": 0.98,
  "plate_color": "blue"
}
```

---

## 3. 维度表：设备拓扑（人工维护）

```sql
CREATE TABLE IF NOT EXISTS dim_device_topo (
    from_device_id  VARCHAR(64),
    to_device_id    VARCHAR(64),
    relation        VARCHAR(16) DEFAULT 'adjacent',
    avg_transit_sec INT COMMENT '平均转移秒数（可选）'
)
UNIQUE KEY(from_device_id, to_device_id)
DISTRIBUTED BY HASH(from_device_id) BUCKETS 8;
```

---

## 4. 第一层：ODS 贴源层（两张表）

### 4.1 人脸 ODS 表 `ods_face_event`

```sql
CREATE TABLE IF NOT EXISTS ods_face_event (
    event_id            VARCHAR(64)   COMMENT '事件唯一ID',
    event_type          VARCHAR(32)   COMMENT 'face_detection / face_feature',
    device_id           VARCHAR(64)   COMMENT '摄像头ID',
    ts                  BIGINT        COMMENT '事件时间戳(毫秒)',
    ingest_ts           BIGINT        COMMENT '接入时间戳(毫秒)',
    kafka_partition     INT           COMMENT 'Kafka分区',
    kafka_offset        BIGINT        COMMENT 'Kafka偏移量',
    
    track_id            VARCHAR(64)   COMMENT '单路跟踪ID',
    bbox_x              INT,
    bbox_y              INT,
    bbox_w              INT,
    bbox_h              INT,
    score               DOUBLE        COMMENT '检测置信度',
    
    feature_id          VARCHAR(64)   COMMENT '人脸特征ID（face_feature事件）',
    feature_score       DOUBLE        COMMENT '特征匹配置信度',
    feature_version     VARCHAR(16)   COMMENT '特征版本',
    
    face_gender         VARCHAR(8)    COMMENT 'male/female/unknown',
    face_age            INT           COMMENT '年龄估计值',
    face_glasses        BOOLEAN       COMMENT '是否戴眼镜',
    face_mask           BOOLEAN       COMMENT '是否戴口罩',
    face_quality        DOUBLE        COMMENT '人脸质量分'
)
DUPLICATE KEY(event_id)
PARTITION BY RANGE(ts) ()
DISTRIBUTED BY HASH(device_id) BUCKETS 32
PROPERTIES ("replication_num" = "3");
```

### 4.2 车牌 ODS 表 `ods_plate_event`

```sql
CREATE TABLE IF NOT EXISTS ods_plate_event (
    event_id            VARCHAR(64),
    event_type          VARCHAR(32)   COMMENT 'vehicle_detection / plate_ocr',
    device_id           VARCHAR(64),
    ts                  BIGINT,
    ingest_ts           BIGINT,
    kafka_partition     INT,
    kafka_offset        BIGINT,
    
    track_id            VARCHAR(64),
    bbox_x              INT,
    bbox_y              INT,
    bbox_w              INT,
    bbox_h              INT,
    score               DOUBLE,
    
    plate_no            VARCHAR(16)   COMMENT '车牌号码',
    plate_score         DOUBLE        COMMENT '车牌识别置信度',
    plate_color         VARCHAR(16)   COMMENT '车牌颜色',
    
    vehicle_type        VARCHAR(16)   COMMENT 'car/truck/bus/motor',
    vehicle_color       VARCHAR(16),
    vehicle_brand       VARCHAR(32)
)
DUPLICATE KEY(event_id)
PARTITION BY RANGE(ts) ()
DISTRIBUTED BY HASH(device_id) BUCKETS 32
PROPERTIES ("replication_num" = "3");
```

### 4.3 ODS 数据导入（Routine Load）

```sql
-- 人脸
CREATE ROUTINE LOAD ods_face_load ON ods_face_event
COLUMNS(event_id, event_type, device_id, ts, track_id, bbox_x, bbox_y, bbox_w, bbox_h, score,
        feature_id, feature_score, feature_version, face_gender, face_age, face_glasses, face_mask, face_quality,
        ingest_ts=unix_timestamp()*1000, kafka_partition, kafka_offset)
PROPERTIES ("desired_concurrent_number" = "3", "format" = "json")
FROM KAFKA ("kafka_broker_list" = "kafka:9092", "kafka_topic" = "ods.face.raw", "property.group.id" = "doris_ods_face");

-- 车牌
CREATE ROUTINE LOAD ods_plate_load ON ods_plate_event
COLUMNS(event_id, event_type, device_id, ts, track_id, bbox_x, bbox_y, bbox_w, bbox_h, score,
        plate_no, plate_score, plate_color, vehicle_type, vehicle_color, vehicle_brand,
        ingest_ts=unix_timestamp()*1000, kafka_partition, kafka_offset)
PROPERTIES ("desired_concurrent_number" = "3", "format" = "json")
FROM KAFKA ("kafka_broker_list" = "kafka:9092", "kafka_topic" = "ods.plate.raw", "property.group.id" = "doris_ods_plate");
```

---

## 5. 第二层：DWD 明细层（两张表）

### 5.1 人脸 DWD 表 `dwd_face_detail`

```sql
CREATE TABLE IF NOT EXISTS dwd_face_detail (
    event_id            VARCHAR(64),
    event_type          VARCHAR(32),
    device_id           VARCHAR(64),
    ts                  BIGINT,
    track_id            VARCHAR(64),
    
    global_face_id      VARCHAR(64)   COMMENT '跨设备人脸唯一ID（有特征ID时等于特征ID，否则规则生成）',
    
    bbox_x              INT,
    bbox_y              INT,
    bbox_w              INT,
    bbox_h              INT,
    center_x            INT           COMMENT '框中心X',
    center_y            INT           COMMENT '框中心Y',
    score               DOUBLE,
    
    feature_id          VARCHAR(64),
    feature_score       DOUBLE,
    feature_version     VARCHAR(16),
    
    face_gender         VARCHAR(8),
    face_age            INT,
    face_glasses        BOOLEAN,
    face_mask           BOOLEAN,
    face_quality        DOUBLE,
    
    attrs               JSON          COMMENT '其他未展开字段'
)
UNIQUE KEY(event_id)
DISTRIBUTED BY HASH(device_id) BUCKETS 32
PROPERTIES (
    "enable_unique_key_merge_on_write" = "true",
    "replication_num" = "3"
);
```

### 5.2 车牌 DWD 表 `dwd_plate_detail`

```sql
CREATE TABLE IF NOT EXISTS dwd_plate_detail (
    event_id            VARCHAR(64),
    event_type          VARCHAR(32),
    device_id           VARCHAR(64),
    ts                  BIGINT,
    track_id            VARCHAR(64),
    
    global_plate_id     VARCHAR(64)   COMMENT '跨设备车牌唯一ID（有车牌号时等于MD5(plate_no)，否则规则生成）',
    
    bbox_x              INT,
    bbox_y              INT,
    bbox_w              INT,
    bbox_h              INT,
    center_x            INT,
    center_y            INT,
    score               DOUBLE,
    
    plate_no            VARCHAR(16),
    plate_score         DOUBLE,
    plate_color         VARCHAR(16),
    
    vehicle_type        VARCHAR(16),
    vehicle_color       VARCHAR(16),
    vehicle_brand       VARCHAR(32),
    
    attrs               JSON
)
UNIQUE KEY(event_id)
DISTRIBUTED BY HASH(device_id) BUCKETS 32
PROPERTIES (
    "enable_unique_key_merge_on_write" = "true",
    "replication_num" = "3"
);
```

### 5.3 DWD 层 global_id 生成规则

| 类型 | 强 ID 来源 | 弱 ID 生成规则（当强 ID 缺失时） |
|------|-----------|-------------------------------|
| 人脸 | `feature_id`（特征 ID）非空且 score > 阈值 | `MD5(device_id + track_id + 属性哈希)` |
| 车牌 | `plate_no`（车牌号）非空且 score > 阈值 | `MD5(device_id + track_id + vehicle_type + vehicle_color)` |

**Flink 作业要点**：
- 源表水印：`WATERMARK FOR ts AS ts - INTERVAL '2' SECOND`
- 使用 KeyedProcessFunction 维护状态，对同一 `track_id` 合并检测事件与特征/识别事件，补全字段。
- 计算 `center_x` / `center_y` 并生成 `global_*_id`。
- 通过 Doris Connector Upsert 写入 DWD 表。

---

## 6. 第三层：DWS 汇总层（两张表）

### 6.1 人脸 DWS 表 `dws_face_trace_1h`

```sql
CREATE TABLE IF NOT EXISTS dws_face_trace_1h (
    stat_date           DATE          COMMENT '统计日期',
    stat_hour           TINYINT       COMMENT '统计小时',
    global_face_id      VARCHAR(64)   COMMENT '人脸全局ID',
    device_id           VARCHAR(64)   COMMENT '设备ID',
    
    first_ts            BIGINT        COMMENT '该小时首次出现时间',
    last_ts             BIGINT        COMMENT '该小时末次出现时间',
    appear_cnt          BIGINT        COMMENT '出现次数',
    
    avg_center_x        DOUBLE        COMMENT '平均中心X',
    avg_center_y        DOUBLE        COMMENT '平均中心Y',
    
    face_gender         VARCHAR(8),
    face_age            INT           COMMENT '年龄中位数',
    face_glasses        BOOLEAN       COMMENT '是否戴眼镜（众数）',
    face_mask           BOOLEAN       COMMENT '是否戴口罩（众数）',
    
    session_id          VARCHAR(64)   COMMENT '所属会话ID'
)
DUPLICATE KEY(stat_date, stat_hour, global_face_id, device_id)
PARTITION BY RANGE(stat_date) ()
DISTRIBUTED BY HASH(global_face_id) BUCKETS 32
PROPERTIES ("replication_num" = "3");
```

### 6.2 车牌 DWS 表 `dws_plate_trace_1h`

```sql
CREATE TABLE IF NOT EXISTS dws_plate_trace_1h (
    stat_date           DATE,
    stat_hour           TINYINT,
    global_plate_id     VARCHAR(64),
    device_id           VARCHAR(64),
    
    first_ts            BIGINT,
    last_ts             BIGINT,
    appear_cnt          BIGINT,
    
    avg_center_x        DOUBLE,
    avg_center_y        DOUBLE,
    
    plate_no            VARCHAR(16),
    vehicle_type        VARCHAR(16),
    vehicle_color       VARCHAR(16),
    vehicle_brand       VARCHAR(32),
    
    session_id          VARCHAR(64)
)
DUPLICATE KEY(stat_date, stat_hour, global_plate_id, device_id)
PARTITION BY RANGE(stat_date) ()
DISTRIBUTED BY HASH(global_plate_id) BUCKETS 32
PROPERTIES ("replication_num" = "3");
```

### 6.3 DWS 层 Flink 会话作业逻辑

| 功能 | 描述 |
|------|------|
| **会话切割** | 按 `global_*_id` 分区。若相邻事件时间差 > 30 分钟，或设备切换不满足 `dim_device_topo` 且属性变化过大，则开启新会话。 |
| **跨摄像头合并** | 利用广播的 `dim_device_topo` 及属性相似度（人脸：性别+年龄区间+眼镜口罩；车牌：车型+颜色）将临时 ID 合并为同一 `global_*_id`。 |
| **小时聚合** | 1 小时滚动窗口聚合，输出至 DWS 表。 |

---

## 7. 第四层：ADS 应用层（两张表）

### 7.1 人脸 ADS 表 `ads_face_app`

```sql
CREATE TABLE IF NOT EXISTS ads_face_app (
    ts                  BIGINT        COMMENT '事件时间',
    date                DATE          COMMENT '日期',
    global_face_id      VARCHAR(64),
    device_id           VARCHAR(64),
    
    face_gender         VARCHAR(8),
    face_age            INT,
    face_glasses        BOOLEAN,
    face_mask           BOOLEAN,
    
    session_id          VARCHAR(64),
    session_start_ts    BIGINT,
    session_end_ts      BIGINT,
    
    companion_face_id   VARCHAR(64)   COMMENT '同行人脸global_id（离线计算填充）'
)
DUPLICATE KEY(global_face_id, ts)
PARTITION BY RANGE(date) ()
DISTRIBUTED BY HASH(global_face_id) BUCKETS 16
PROPERTIES ("replication_num" = "3");
```

### 7.2 车牌 ADS 表 `ads_plate_app`

```sql
CREATE TABLE IF NOT EXISTS ads_plate_app (
    ts                  BIGINT,
    date                DATE,
    global_plate_id     VARCHAR(64),
    plate_no            VARCHAR(16),
    device_id           VARCHAR(64),
    
    vehicle_type        VARCHAR(16),
    vehicle_color       VARCHAR(16),
    vehicle_brand       VARCHAR(32),
    
    session_id          VARCHAR(64),
    session_start_ts    BIGINT,
    session_end_ts      BIGINT,
    
    companion_plate     VARCHAR(16)   COMMENT '伴随车牌',
    companion_global_id VARCHAR(64)
)
DUPLICATE KEY(global_plate_id, ts)
PARTITION BY RANGE(date) ()
DISTRIBUTED BY HASH(global_plate_id) BUCKETS 16
PROPERTIES ("replication_num" = "3");
```

### 7.3 ADS 数据刷新

- **实时部分**：Flink DWS 作业直接将会话信息（`session_id`, `session_start_ts`, `session_end_ts`）连同明细写入 ADS 表。
- **离线部分**：每日调度 SQL 计算伴随关系等复杂指标，更新 ADS 表相应字段。

---

## 8. 查询示例

### 8.1 查询某人脸跨摄像头轨迹

```sql
SELECT device_id, ts, face_gender, face_age
FROM ads_face_app
WHERE global_face_id = 'xxx'
ORDER BY ts;
```

### 8.2 查询某车牌完整行驶路径

```sql
SELECT device_id, ts, vehicle_color, plate_no
FROM ads_plate_app
WHERE plate_no = '沪A12345'
ORDER BY ts;
```

### 8.3 人脸聚集告警（同摄像头短时间内多人脸）

```sql
SELECT device_id, DATE_TRUNC('second', FROM_UNIXTIME(ts/1000)) AS sec, COUNT(*) AS face_cnt
FROM dwd_face_detail
WHERE device_id = 'cam_01' AND ts > UNIX_TIMESTAMP(NOW() - INTERVAL 10 MINUTE)*1000
GROUP BY device_id, sec
HAVING face_cnt >= 5;
```

### 8.4 车牌伴随分析（同摄像头短时间多车）

```sql
SELECT a.plate_no AS car_a, b.plate_no AS car_b, COUNT(*) AS meet_times
FROM dwd_plate_detail a
JOIN dwd_plate_detail b 
  ON a.device_id = b.device_id 
  AND ABS(a.ts - b.ts) < 5000
  AND a.plate_no != b.plate_no
WHERE a.ts > UNIX_TIMESTAMP(NOW() - INTERVAL 1 DAY)*1000
GROUP BY a.plate_no, b.plate_no
HAVING meet_times >= 3;
```

---

## 9. 落地执行清单

| 序号 | 任务 | 负责角色 | 产出物 |
|------|------|---------|-------|
| 1 | 确认人脸、车牌消息 Schema 并推送至 Kafka | 算法/后端 | 两个 Topic 正常生产 |
| 2 | 人工录入设备拓扑表 `dim_device_topo` | 现场工程 | 拓扑数据初始化 |
| 3 | 创建 ODS 表及 Routine Load | 数据开发 | 两张 ODS 表 + 两个 Routine Load |
| 4 | 开发 Flink `job_dwd_attribution`（人脸/车牌各一个作业） | 数据开发 | 可运行 Flink 任务 |
| 5 | 创建 DWD 表 | 数据开发 | 两张 DWD 表 |
| 6 | 开发 Flink `job_dws_session`（人脸/车牌各一个作业） | 数据开发 | 会话切割与小时聚合 |
| 7 | 创建 DWS 表 | 数据开发 | 两张 DWS 表 |
| 8 | 创建 ADS 表并配置刷新调度 | 数据开发 | 两张 ADS 表及配套 SQL |
| 9 | 配置监控告警 | 运维/开发 | Kafka Lag、Flink Checkpoint、Doris 导入延迟 |

---

## 10. 监控指标

| 指标 | 告警阈值 | 查看方式 |
|------|---------|---------|
| Kafka 消费延迟 | > 5000 条 | Kafka Manager |
| Flink Checkpoint 失败率 | > 5% | Flink Web UI |
| Doris Routine Load 错误行数 | 持续增长 | `SHOW ROUTINE LOAD` |
| DWD global_id 生成率（强 ID 占比） | 低于 60% 需关注弱 ID 合并准确性 | 自定义 Metric |
| DWS 会话数量波动 | 偏离 7 日均值 50% | Doris SQL |

---

**文档版本**：v3.0  
**最后更新**：2026-04-15  
**适用范围**：人脸与车牌大数据独立四层架构落地实施