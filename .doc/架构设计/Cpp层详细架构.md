# C++ 层详细架构 (RUNTIME / TASK 模块)

> 基于整体架构文件 V9.18.0 + RUNTIME / TASK 模块源代码深入分析
> 代码规模: RUNTIME + TASK 双模块 / C++17
> 分析日期: 2026-08-12

---

## 一、总体定位

本文档覆盖 EasyAIot 的两个 C++ 模块：

- **TASK** — 极致性能 YOLO 推理引擎，用 C++ 实现从 RTSP 拉流 → YOLO 推理 → RTMP 推流 → **MQTT 告警总线** 的完整闭环，跨平台 (Windows/Linux)，面向工控机和边缘盒子部署。
- **RUNTIME** — C++ 帧执行器，是 TASK 在生产部署中的上层壳：拉流 → 解码 → 调用 TASK 推理 → 把检测结果（带框）回推 `ai_rtmp` → 经 **AlgoMqttBus** 发布告警/抓拍/后处理 MQTT 信封给 iot-sink，同时仍以 HTTP 心跳汇报给 VIDEO。本机与集群计算节点均支持 `executor=cpp`。

```
┌─────────────────────────────────────────────────────────────────────┐
│         RUNTIME / TASK — C++ 边缘推理引擎 + MQTT 告警总线              │
│         C++17 / ONNX Runtime / FFmpeg / OpenCV                        │
│                                                                     │
│  RTSP 摄像头                                                         │
│      │                                                               │
│      ▼                                                               │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────┐               │
│  │ FFmpeg   │ →  │ YOLOv11      │ →  │ RTMPEncoder  │ → RTMP 推流   │
│  │ 拉流解码  │    │ ONNX 推理    │    │ H.264 编码   │   (ai_rtmp)   │
│  └──────────┘    │ (多线程池)   │    └──────────────┘               │
│                  └──────┬───────┘                                    │
│                         │ 检测结果                                   │
│                  ┌──────┴───────┐                                    │
│                  ▼              ▼                                    │
│           ┌──────────┐   ┌──────────────┐                           │
│           │ Draw     │   │ AlgoMqttBus  │ → MQTT publish → iot-sink │
│           │ 绘制标注  │   │ 告警总线     │   (QoS1, 三对 topic)      │
│           └──────────┘   └──────────────┘                           │
│                                                                     │
│  HTTP 控制服务器 (:8000+taskId) ← 运行时动态启停推流                   │
│  HTTP 心跳 → VIDEO（保持不变，非告警链路）                            │
└─────────────────────────────────────────────────────────────────────┘
```

| 对比维度 | RUNTIME / TASK (C++) | VIDEO (Python) |
|----------|----------------------|----------------|
| **定位** | 极致性能 C++ 帧执行器 + YOLO 推理引擎 | 任务编排/预览/任务管理/HTTP 心跳层 |
| **内存** | ~200-500MB | 中等（Python 运行时） |
| **推理引擎** | ONNX Runtime (GPU/DirectML/CUDA) | 不直接推理，下沉到 C++ 执行器 |
| **推理速度** | 最快 (原生 C++) | 取决于后端 executor |
| **灵活性** | 编译后固定，配置驱动 | CLI/HTTP 动态下发任务 |
| **告警上报** | MQTT publish → iot-sink（AlgoMqttBus） | 不发告警，仅收 HTTP 心跳 |
| **部署** | 编译二进制，本机或集群 `executor=cpp` | Python 服务（FastAPI） |

---

## 二、技术栈

| 组件 | 版本/说明 | 用途 |
|------|----------|------|
| **语言标准** | C++17 | `std::atomic`, `std::unique_ptr`, `std::make_unique`, `std::optional` |
| **推理引擎** | ONNX Runtime (C++ API) | YOLOv11 ONNX 模型加载和推理 |
| **视频解码** | FFmpeg libav (C API) | RTSP 拉流、帧解码 (H.264/H.265) |
| **视频编码** | FFmpeg libavcodec/libavformat | H.264 编码 + RTMP 推流 |
| **图像处理** | OpenCV 4.x | 帧格式转换、图像绘制、颜色空间转换 |
| **HTTP 通信** | cpp-httplib (嵌入) | HTTP 控制服务器（运行时启停推流）+ HTTP 心跳客户端（→VIDEO）。**告警链路不再走 HTTP** |
| **MQTT 通信** | 自实现 MQTT 3.1.1 客户端（裸 TCP socket） | AlgoMqttBus 发布告警/抓拍/后处理信封到 iot-sink，无外部 MQTT SDK 依赖 |
| **JSON 处理** | jsoncpp | 告警信封 JSON 构建和区域解析 |
| **日志** | glog (Google Log) | 结构化日志输出 |
| **构建** | CMake 3.5+ | 跨平台构建系统 |
| **包管理** | vcpkg (Windows) / apt (Linux) | 依赖管理 |

---

## 三、目录结构

### 3.1 RUNTIME 模块（C++ 帧执行器 + MQTT 告警总线）

```
RUNTIME/
├── CMakeLists.txt                     ← CMake 主构建配置
│
├── src/                               ← 核心源代码
│   ├── main.cpp                       ← 程序入口：拉流→解码→推理→推流→告警编排
│   ├── Config.h                       ← 配置结构体（含 mqttBrokerUrls / mqttUsername /
│   │                                    mqttPassword / mqttClientId / mqttTenant /
│   │                                    computeNodeId / algoBusTransport 等新字段）
│   ├── ConfigParser.cpp               ← INI 解析器实现（含 [mqtt] 段解析）
│   ├── Detech.cpp                     ← 帧执行主循环：调度 TASK 推理、推 ai_rtmp、
│   │                                    调 AlgoMqttBus 发布告警、HTTP 心跳→VIDEO
│   ├── AlgoMqttBus.h                  ← MQTT 告警总线声明
│   ├── AlgoMqttBus.cpp                ← 自实现 MQTT 3.1.1 客户端（裸 TCP socket，
│   │                                    CONNECT/CONNACK/PUBLISH QoS1/PUBACK/DISCONNECT）
│   └── (复用 TASK 的 Yolov11* / RTMPEncoder / Draw / Datatype)
│
├── config/
│   └── config.example.ini             ← 含 [mqtt] / [algo_bus] / [compute] 段的模板
│
└── README.md                          ← RUNTIME 说明（executor=cpp 入口）
```

### 3.2 TASK 模块（YOLO 推理引擎，本文档原主体）

```
TASK/
├── CMakeLists.txt                     ← CMake 主构建配置 (181 行)
├── CMakeLists_Windows.txt             ← Windows 备用构建配置
│
├── src/                               ← 核心源代码 (14 文件)
│   ├── main.cpp                       ← 程序入口 (147 行)
│   ├── Manage.h                       ← Server 类声明 (36 行)
│   ├── Manage.cpp                     ← Server 生命周期管理 (106 行)
│   ├── Manage_Windows.h               ← Windows 备用信号处理
│   ├── Config.h                       ← 配置结构体 (40 行)
│   ├── ConfigParser.h                 ← 配置解析器声明 (58 行)
│   ├── ConfigParser.cpp               ← INI 解析器实现
│   ├── Detech.h                       ← 核心检测引擎声明 (124 行)
│   ├── Detech.cpp                     ← 核心检测引擎实现 (43,803 字节)
│   ├── Yolov11Engine.h                ← ONNX Runtime 封装声明 (28 行)
│   ├── Yolov11Engine.cpp              ← ONNX 推理实现
│   ├── Yolov11ThreadPool.h            ← 线程池声明 (42 行)
│   ├── Yolov11ThreadPool.cpp          ← Linux 线程池实现
│   ├── Yolov11ThreadPool_Windows.cpp  ← Windows 线程池实现
│   ├── RTMPEncoder.h                  ← RTMP 编码器声明 (70 行)
│   ├── RTMPEncoder.cpp                ← RTMP 编码实现
│   ├── AlgoMqttBus.h                  ← MQTT 告警总线声明（取代原 AlarmCallback.h）
│   ├── AlgoMqttBus.cpp                ← MQTT 告警总线实现（取代原 HTTP 告警实现 AlarmCallback.cpp）
│   ├── Draw.h                         ← 绘制函数声明 (14 行)
│   ├── Draw.cpp                       ← 检测框绘制 (38 行)
│   └── Datatype.h                     ← 共享数据类型 (57 行)
│
├── config/                            ← 配置文件
│   ├── config.example.ini             ← 带注释的配置模板
│   ├── test.ini                       ← 主测试配置 (完整)
│   ├── test_rtsp_only.ini             ← 纯 RTSP 测试 (最小)
│   ├── test-hikvision-main.ini        ← 海康主码流
│   ├── test-hikvision-sub.ini         ← 海康子码流
│   ├── test-dahua.ini                 ← 大华摄像头
│   └── test-generic.ini               ← 通用摄像头
│
├── models/                            ← AI 模型
│   ├── yolo11n.onnx                   ← YOLOv11n ONNX 模型 (~12.7MB)
│   └── coco.names                     ← COCO 80 类别名称
│
├── scripts/                           ← 辅助脚本
│   ├── download_models.py             ← 模型下载工具
│   ├── export_yolo_for_directml.py    ← YOLO DirectML 导出
│   └── download_yolo_model.ps1        ← PowerShell 模型下载
│
├── deploy/                            ← 部署目标
│   ├── ONNXRuntime/onnx_inference.py  ← Python ONNX Runtime + ByteTrack
│   └── ncnn/cpp/CMakeLists.txt        ← ncnn 部署 (Rockchip RK3588)
│
├── 3rdparty/cpp-httplib/              ← 嵌入的 HTTP 库 (仅头文件)
│   └── httplib.h
│
├── zlm-config.ini                     ← ZLMediaKit 流媒体配置
├── zlm_lowlatency.ini                 ← ZLMediaKit 低延迟配置
├── srs-low-latency.conf               ← SRS 低延迟配置
│
├── build.bat                          ← Windows 构建脚本
├── 启动TASK.bat                       ← 启动脚本
├── fix_config_parser.ps1              ← ConfigParser 修复脚本
│
├── BUILD_GUIDE.md                     ← 构建指南
├── DEPLOYMENT_GUIDE.md                ← 部署指南
├── TEST_GUIDE.md                      ← 测试指南
├── WINDOWS_DEPLOYMENT_GUIDE.md        ← Windows 部署指南
├── COMPREHENSIVE_CODE_ANALYSIS.md     ← 代码分析文档
├── README.md / README_en.md           ← 说明文档
└── STEP1_SUMMARY.md                   ← 阶段总结
```

---

## 四、类层次结构

```
main.cpp
  │
  ├── ConfigParser::parse("config.ini", config)
  │     └── Config 结构体 (RTSP/RTMP/Hook/模型路径/区域/线程数)
  │
  └── Server(config)
        │  ┌─ std::atomic<bool> _isRun
        │  ├─ std::atomic<bool> _isTerminal
        │  └─ std::unique_ptr<Detech> _detectHandle
        │
        ├── start() → Detech::start()
        │     ├── _init_media_player()    ← FFmpeg RTSP 解码初始化
        │     ├── _init_yolo11_detector() ← YOLO 线程池初始化
        │     ├── _init_media_pusher()    ← RTMP 编码器初始化
        │     ├── _init_media_alarmer()   ← 告警回调初始化
        │     ├── _init_http_client()     ← HTTP 客户端初始化
        │     ├── _init_control_server()  ← HTTP 控制服务器
        │     └── → _display_video_loop() ← 主循环线程
        │           ├── av_read_frame()   ← FFmpeg 解码
        │           ├── Yolov11ThreadPool::submitTask() ← 推理提交
        │           ├── Yolov11ThreadPool::getTargetResultNonBlock() ← 非阻塞获取
        │           ├── DrawDetections()  ← 绘制检测框
        │           ├── _decode_frame_alarm() ← 告警检测
        │           ├── RTMPEncoder::encodeAndPush() ← RTMP 推流
        │           └── cv::imshow()      ← 本地显示
        │
        └── stop() → _detectHandle.reset()
```

---

## 五、模块详解

### 5.1 main.cpp — 程序入口

```
main(argc, argv)
  │
  ├── 1. 打印 Banner (TASK ASCII Art)
  ├── 2. 参数校验: TASK.exe <config.ini>
  ├── 3. google::InitGoogleLogging()     ← 日志初始化
  ├── 4. ConfigParser::parse(config_file, config) ← 解析配置
  ├── 5. 打印配置摘要
  ├── 6. Server(config)                 ← 创建服务实例
  ├── 7. server.start()                 ← 启动
  ├── 8. server.waitForShutdown()       ← 等待信号 (Ctrl+C)
  └── 9. server.stop() + 清理
```

**关键设计**:
- 单一配置文件驱动: 所有参数（视频源/模型/告警/区域）通过 INI 文件配置
- 全局 `g_server` 指针用于信号处理
- 异常安全: `try/catch` 包裹整个生命周期

### 5.2 Manage (Server) — 生命周期管理

```cpp
class Server {
    std::atomic<bool> _isRun{false};         // 运行标志
    std::atomic<bool> _isTerminal{false};    // 终止标志
    Config _local;                            // 配置副本
    std::unique_ptr<Detech> _detectHandle;    // 独占所有权
};
```

**职责**:
- 创建和管理 `Detech` 核心引擎的生命周期
- 跨平台信号处理注册 (Windows: `SetConsoleCtrlHandler`, Linux: `sigaction`)
- `waitForShutdown()`: 10ms 轮询循环等待退出信号
- 析构时自动调用 `stop()` 清理资源

**信号处理流程**:
```
Ctrl+C → procSignal(s) → s_exit.store(1)
                              │
              waitForShutdown() 轮询检测 → break → stop()
```

### 5.3 Config / ConfigParser — 配置系统

**Config 结构体**:

| 字段 | 类型 | 来源 INI Section | 说明 |
|------|------|-----------------|------|
| `rtspUrl` | string | `[video]` | RTSP 输入流地址 |
| `rtmpUrl` | string | `[video]` | RTMP 推流目标地址（生产部署推 `ai_rtmp`） |
| `hookHttpUrl` | string | `[alarm]` | **@Deprecated** 原 HTTP 告警回调 URL；仅当 `algoBusTransport=http` 回退时使用，默认链路已改 MQTT |
| `enableAI` | bool | `[ai]` | 是否启用 AI 推理 |
| `enableRtmp` | bool | `[features]` | 是否启用 RTMP 推流 |
| `enableDrawRtmp` | bool | `[features]` | 是否在推流中绘制检测框 |
| `enableAlarm` | bool | `[features]` | 是否启用告警检测 |
| `modelPaths` | map | `[ai]` | 模型文件路径 |
| `modelClasses` | map | `[ai]` | 类别文件路径 |
| `threadNums` | int | `[ai]` | 推理线程数 (默认 3) |
| `videoWidth/Height` | int | `[video]` | 视频分辨率 |
| `rtmpFps` | int | `[video]` | 推流帧率 |
| `alarmConfidenceThreshold` | float | `[alarm]` | 告警置信度阈值 (默认 0.6) |
| `alarmCooldownTime` | int | `[alarm]` | 告警冷却时间 (秒) |
| `taskId` | string | `[task]` | 任务 ID |
| `controlPort` | int | `[task]` | HTTP 控制端口 (8000 + taskId) |
| `regions` | map | `[regions]` | JSON 多边形告警区域 |
| `mqttBrokerUrls` | string | `[mqtt]` | MQTT broker 地址列表，逗号分隔（如 `127.0.0.1:1883, broker2:1883`）；自动去除 `tcp://` / `mqtt://` 前缀，缺省端口 1883。环境变量 `MQTT_BROKER_URLS` 覆盖；默认 `127.0.0.1:1883`（本机 EMQX） |
| `mqttUsername` | string | `[mqtt]` | MQTT 用户名；环境变量 `MQTT_ALGO_USERNAME` 覆盖 |
| `mqttPassword` | string | `[mqtt]` | MQTT 密码；环境变量 `MQTT_ALGO_PASSWORD` 覆盖 |
| `mqttClientId` | string | `[mqtt]` | MQTT client id 前缀；运行时自动加 `-pub-<uuid8>` 后缀避免冲突；环境变量 `MQTT_ALGO_CLIENT_ID` 覆盖 |
| `mqttTenant` | string | `[mqtt]` | 租户标识，写入 envelope 的 `tenant` 字段；默认 `default` |
| `algoBusTransport` | string | `[algo_bus]` | 总开关。默认空=MQTT；`http` / `off` / `0` / `false` / `no` = 关闭 MQTT 回退 HTTP（兼容旧部署） |
| `computeNodeId` | string | `[compute]` | 计算节点 ID（集群部署时由 iot-node 分配，写入 envelope 元数据） |

**ConfigParser 功能**:
- 自定义 INI 解析器 (无外部依赖)
- 支持 `[section]` 分段解析，新增 `[mqtt]` / `[algo_bus]` / `[compute]` 段
- 布尔值: `true/false`, `1/0`, `yes/no`, `on/off`
- JSON 区域解析: `[[x1,y1],[x2,y2],[x3,y3]...]` → `vector<cv::Point>`
- Trim 空白、注释跳过、错误报告
- 环境变量覆盖：`MQTT_BROKER_URLS` / `MQTT_ALGO_USERNAME` / `MQTT_ALGO_PASSWORD` / `MQTT_ALGO_CLIENT_ID` 优先于 INI

### 5.4 Detech — 核心检测引擎 (最复杂的类，~44KB)

```
Detech 初始化流程:

start()
  ├── _init_media_player()
  │     ├── avformat_open_input()       ← 打开 RTSP 流
  │     ├── avformat_find_stream_info() ← 探测流信息
  │     ├── avcodec_find_decoder()      ← 查找解码器
  │     └── avcodec_open2()             ← 打开解码器
  │
  ├── _init_yolo11_detector()
  │     └── Yolov11ThreadPool::setUp(model, classes, threadNums)
  │
  ├── _init_media_pusher()
  │     └── RTMPEncoder::init(rtmpUrl, W, H, FPS)
  │
  ├── _init_media_alarmer()
  │     └── AlgoMqttBus 初始化（resolveBrokers 解析 mqttBrokerUrls，
  │                        逐个 TCP connect → CONNECT(level 4, clean session,
  │                        keepalive 30) → 等 CONNACK；全失败才返回 false）
  │
  ├── _init_http_client()
  │     └── httplib::Client 初始化（仅用于控制面与 HTTP 心跳→VIDEO；
  │                        告警链路已不走 HTTP）
  │
  ├── _startAlarmSenderThread()         ← MQTT publish 线程（消费告警队列）
  ├── _startControlServer()             ← HTTP 控制服务器线程
  └── std::thread(&Detech::_display_video_loop) ← 主循环线程
```

**主视频循环 `_display_video_loop()`**:

```
while (_isRun) {
    av_read_frame()                     ← 读取解码帧
    ↓
    每 8 帧执行一次 AI:
    └── Yolov11ThreadPool::submitTask(frame, 0, frameId)
    └── Yolov11ThreadPool::getTargetResultNonBlock(objects, 0, frameId)
    ↓
    if (enableDrawRtmp) {
        DrawDetections(frame, objects)  ← 绘制检测框
        _drawAlarmRegions(frame)        ← 绘制报警区域
    }
    ↓
    if (enableAlarm && objects in region) {
        _publishAlarm(objects, regionName)
            └── alarmQueue.push()       ← 入队 (MAX=20)，由 MQTT publish 线程异步消费
    }
    ↓
    if (_streamingEnabled && enableRtmp) {
        RTMPEncoder::encodeAndPush(frame) ← 编码 + 推流
    }
    ↓
    cv::imshow("TASK", frame)           ← 本地预览
    cv::waitKey(1)
}
```

**告警区域检测**:
```
_isInAlarmRegion(centerX, centerY):
    cv::pointPolygonTest(region, point)  ← 点在多边形内判断
```

**告警队列系统 (生产者-消费者，经 AlgoMqttBus 发布)**:
```
主线程 (生产者)                    MQTT publish 线程 (消费者)
    │                                    │
    ├─ 检测到目标在告警区域              │
    ├─ _checkAlarmCooldown()            │
    ├─ _publishAlarm()                  │
    │   └─ lock(mutex)                  │
    │   └─ alarmQueue.push(data)        │
    │   └─ cv.notify_one()  ──────────→ ├─ cv.wait()
    │   └─ unlock(mutex)                ├─ alarmQueue.pop()
    │                                    ├─ normalizeAlertPayload() 把扁平 VIDEO-hook
    │                                    │   JSON 规范为嵌套 alert 结构
    │                                    ├─ build envelope {version,msgId,msgType,
    │                                    │   tenant,ts,payload}
    │                                    ├─ AlgoMqttBus::publish(topic, msgType, payload)
    │                                    │   ├─ 选择 broker (resolveBrokers 按序逐个尝试)
    │                                    │   ├─ CONNECT(level 4, clean session, keepalive 30)
    │                                    │   ├─ 等 CONNACK
    │                                    │   ├─ PUBLISH QoS1 (packet id++)
    │                                    │   ├─ 等 PUBACK (超时则切下一个 broker 重试)
    │                                    │   └─ DISCONNECT
    │                                    └─ LOG result
```

**HTTP 控制服务器 (动态推流控制)**:
```
控制服务器线程:
    httplib::Server svr;
    svr.Post("/start", [&]() {
        startStreaming();  ← 设置 _streamingEnabled = true
        return "OK";
    });
    svr.Post("/stop", [&]() {
        stopStreaming();   ← 设置 _streamingEnabled = false
        return "OK";
    });
    svr.Get("/status", [&]() {
        return isStreaming() ? "RUNNING" : "STOPPED";
    });
    svr.listen("0.0.0.0", controlPort);  ← 8000+taskId
```

### 5.5 Yolov11Engine — ONNX Runtime 封装

```cpp
class Yolov11Engine {
    Ort::Env onnxEnv{nullptr};               // ONNX 环境
    Ort::SessionOptions onnxSessionOptions;  // 会话选项 (GPU/DirectML)
    Ort::Session onnxSession{nullptr};       // ONNX 推理会话
};
```

**推理流程**:
```
LoadModel(model_path, class_names)
  ├── Ort::Session(env, model_path, options) ← 加载 ONNX 模型
  └── 验证输入/输出张量维度

Run(image, objects)
  ├── 预处理: resize → normalize → NCHW 转换
  ├── Inference(): Ort::Session::Run()
  │     ├── 输入: [1, 3, 640, 640] float
  │     └── 输出: [1, 84, 8400] float (80类 + 4坐标)
  ├── 后处理: NMS (阈值 0.45) + 置信度过滤 (阈值 0.25)
  └── → vector<DetectObject> (x1, y1, x2, y2, class_id, class_name, score)
```

**输入/输出张量格式**:

| 张量 | Shape | 说明 |
|------|-------|------|
| 输入 | `[1, 3, 640, 640]` | NCHW, float32, 归一化到 [0,1] |
| 输出 | `[1, 84, 8400]` | 80 COCO 类 + 4 边界框坐标, 8400 锚点 |
| 输出 | `[1, 1, 8400]` (可选) | 置信度分数 |

### 5.6 Yolov11ThreadPool — 多线程推理池

```
Yolov11ThreadPool
    │
    ├── 每个线程拥有独立的 Yolov11Engine 实例 (ONNX Session 不线程安全)
    │
    ├── tasks 队列: queue<tuple<input_id, frame_id, Mat>>
    │
    ├── submitTask(img, input_id, frame_id)
    │     └── lock → tasks.push() → cv.notify_one()
    │
    ├── worker(id)
    │     └── while(!stop) {
    │             cv.wait() → lock → tasks.pop() → Yolov11Engine::Run()
    │             → img_results[input_id][frame_id] = result
    │         }
    │
    └── getTargetResultNonBlock(objects, input_id, frame_id)
          └── lock → 检查 results 是否就绪 → 返回 (不阻塞)
```

**设计要点**:
- 每个 Worker 线程独立持有 `Yolov11Engine` 实例 — ONNX Session 不是线程安全的
- 非阻塞结果获取: 主循环不等待推理完成，继续处理下一帧
- 每 8 帧触发一次推理: 降低 GPU 负载，保证实时性
- 默认 3 线程: 平衡吞吐量和 GPU 显存

**平台差异**:

| | Linux | Windows |
|--|-------|---------|
| 文件 | `Yolov11ThreadPool.cpp` | `Yolov11ThreadPool_Windows.cpp` |
| 线程同步 | `pthread` 原生 | `std::thread` (C++11) |
| GPU 加速 | CUDA | DirectML (RTX 5060) |

### 5.7 RTMPEncoder — H.264 编码与推流

```cpp
class RTMPEncoder {
    AVFormatContext* _outputCtx;    // RTMP 输出上下文
    AVCodecContext* _codecCtx;      // H.264 编码器上下文 (libx264)
    AVStream* _videoStream;         // 视频流
    SwsContext* _swsCtx;            // BGR24 → YUV420P 颜色转换
    AVFrame* _yuvFrame;             // YUV 帧缓冲
    AVPacket* _packet;              // 编码数据包
    int64_t _frameIndex;            // PTS 时间戳计数器
};
```

**推流流程**:
```
init(rtmpUrl, W, H, FPS)
  ├── avformat_alloc_output_context2()  ← 创建 RTMP 输出上下文
  ├── avcodec_find_encoder(H264)        ← 查找 H.264 编码器
  ├── avcodec_open2()                   ← 配置: preset=veryfast, tune=zerolatency, crf=23
  ├── avformat_write_header()           ← 写入 RTMP 头
  └── sws_getContext(BGR24→YUV420P)     ← 颜色空间转换器

encodeAndPush(cv::Mat BGR)
  ├── sws_scale(BGR → YUV420P)          ← 颜色转换
  ├── avcodec_send_frame()              ← 送入编码器
  ├── avcodec_receive_packet()          ← 获取编码数据
  ├── av_packet_rescale_ts()            ← 时间戳对齐
  ├── av_interleaved_write_frame()      ← 写入 RTMP 流
  └── _frameIndex++

release()
  ├── av_write_trailer()                ← 写入尾部
  └── 释放所有 FFmpeg 资源
```

**低延迟配置**:
- `preset=veryfast`: 编码速度优先
- `tune=zerolatency`: 零延迟调优
- `crf=23`: 质量/码率平衡
- `keyint=FPS*2`: 每 2 秒一个关键帧

### 5.8 AlgoMqttBus — MQTT 告警总线

> 原 HTTP 告警回调 `AlarmCallback`（`httplib::Client::Post` → AI 模块 :5000）已被 `AlgoMqttBus` 取代。心跳仍走 HTTP→VIDEO，不在本节范围。

**定位**：算法事件总线。RUNTIME / TASK 检测到告警后，经 `AlgoMqttBus` 把规范化的 MQTT 信封 publish 到 broker（本机 EMQX 或集群 broker），由 iot-sink 的 `IotAlgoBusMqttHandler` 订阅、落库、转发 Kafka 通知。

**类成员**（关键，语义对照原 AlarmCallback）:

```cpp
class AlgoMqttBus {
    // broker 列表（取代原单一 hookUrl_）
    struct Broker { std::string host; int port; };
    std::vector<Broker> brokers_;     // 解析自 mqttBrokerUrls（去 tcp:// / mqtt:// 前缀，缺省 1883）

    // 鉴权（取代原无鉴权的 HTTP）
    std::string username_;            // mqttUsername / MQTT_ALGO_USERNAME
    std::string password_;            // mqttPassword / MQTT_ALGO_PASSWORD
    std::string clientId_;            // mqttClientId + "-pub-<uuid8>" 后缀
    std::string tenant_;              // 默认 "default"，写入 envelope

    // 裸 TCP socket（取代原 httplib::Client* client_；无外部 MQTT SDK 依赖）
    int sock_;                        // 当前已连接的 broker socket
    uint16_t nextPacketId_;           // QoS1 PUBLISH 的 packet id 自增
};
```

**信封 envelope 格式**（统一三对 topic）:
```json
{
    "version": "1.0",
    "msgId": "<uuid4>",
    "msgType": "alert.notification",
    "tenant": "default",
    "ts": "2026-08-12T08:30:00.000Z",
    "payload": {
        "taskId": 123,
        "alarmType": "region",
        "regionName": "area_1",
        "timestamp": 1698123456789,
        "detections": [
            {
                "x1": 100, "y1": 200, "x2": 300, "y2": 400,
                "confidence": 0.85,
                "class_id": 0,
                "class_name": "person"
            }
        ]
    }
}
```

> `payload` 内的告警业务字段（taskId / alarmType / regionName / timestamp / detections）与原 HTTP 告警 JSON 保持一致；外层 envelope 是 MQTT 总线新增的规范包装。`normalizeAlertPayload()` 负责把原扁平 VIDEO-hook JSON 规范为嵌套 `alert` 结构。

**三对 topic / msgType**:

| 场景 | topic | msgType |
|------|-------|---------|
| 告警通知 | `mqtt/iot-alert-notification` | `alert.notification` |
| 抓拍告警 | `mqtt/iot-snapshot-alert` | `alert.snapshot` |
| 后处理请求 | `mqtt/iot-post-process-request` | `post_process.request` |

> iot-sink 用 `$share/algo-sink/` 共享订阅组消费上述 topic，保证多副本负载分摊。

**MQTT 3.1.1 客户端实现**（无外部 SDK，自行实现，仅发布不订阅）:

```
连接握手（resolveBrokers 按序逐个尝试，全失败才返回 false）:
  for broker in brokers_:
      sock_ = tcp_connect(broker.host, broker.port)      ← 失败则尝试下一个
      send CONNECT(level 4, clean session, keepalive 30s,
                  clientId, username, password)
      wait CONNACK (return code 0 = Accepted)            ← 非 0 或超时则关闭，尝试下一个
      return true
  return false

发布一条告警（QoS1，保证可达）:
  publish(topic, msgType, payload):
      envelope = buildEnvelope(msgType, tenant, payload)
      packet_id = nextPacketId_++
      send PUBLISH(topic, payload=envelope, QoS=1, packet_id)
      wait PUBACK (packet_id 匹配)                       ← 超时则切下一个 broker 重连重试

优雅退出:
  send DISCONNECT
  close(sock_)
```

**关键特性**（对照原 AlarmCallback 的特性清单）:
- Broker 解析（取代原 URL 解析）: 从 `mqttBrokerUrls` 逗号分隔列表分离 host/port，自动去 `tcp://` / `mqtt://` 前缀，缺省端口 1883
- 总开关: `algoBusTransport`（默认空=MQTT；`http` / `off` / `0` / `false` / `no` = 关闭 MQTT，回退原 HTTP 告警，兼容旧部署）
- JSON 构建: 使用 jsoncpp 构建 envelope 与 payload；`normalizeAlertPayload()` 规范化
- 连接测试（取代原 `testConnection()`）: 启动前 `resolveBrokers()` 验证至少一个 broker 可完成 CONNECT→CONNACK 握手
- 生产者-消费者: 通过 Detech 的告警队列异步发送，独立 MQTT publish 线程，不阻塞主循环
- 断线重试: QoS1 PUBACK 超时或 socket 断开，按序切下一个 broker 重连后重发
- 默认 broker: `127.0.0.1:1883`（本机 EMQX），环境变量 `MQTT_BROKER_URLS` 可覆盖

### 5.9 Draw — 检测结果可视化

```
DrawDetections(img, objects)
    对每个检测对象:
    ├── cv::rectangle()  ← 绘制边界框 (红色, 线宽 2)
    ├── cv::getTextSize() ← 计算标签背景大小
    ├── cv::rectangle()  ← 标签背景 (红色填充)
    └── cv::putText()    ← 标签文字 "person:85.0%" (白色)
```

### 5.10 Datatype — 共享数据类型

| 类型 | 说明 |
|------|------|
| `tensor_layout_e` | NCHW / NHWC / UNKNOWN |
| `tensor_datatype_e` | INT8 / UINT8 / FLOAT / FLOAT16 |
| `DetectObject` | 检测结果: bbox + class_id + class_name + score + happen |
| `nn_tensor_type_to_size()` | 数据类型 → 字节数映射 |

---

## 六、线程模型

```
┌────────────────────────────────────────────────────────────────┐
│                   RUNTIME / TASK 线程模型 (6 类线程)             │
│                                                                │
│  ┌─────────────────────┐  ┌──────────────────────────────┐     │
│  │ 主线程               │  │ YOLO Worker × N (默认 3)      │     │
│  │ _display_video_loop │  │ worker(id)                   │     │
│  │ FFmpeg 解码 + 显示  │  │ ONNX Session::Run()          │     │
│  │ + 提交推理任务       │  │ 独立 Yolov11Engine 实例       │     │
│  └─────────┬───────────┘  └──────────────┬───────────────┘     │
│            │                              │                     │
│            │ submitTask()                 │                     │
│            ├─────────────────────────────►│                     │
│            │  <── getTargetNonBlock() ────┤                     │
│            │                              │                     │
│  ┌─────────┴───────────┐  ┌──────────────┴───────────────┐     │
│  │ MQTT publish 线程     │  │ HTTP 控制服务器线程           │     │
│  │ _alarmSenderThread   │  │ _controlServerThread        │     │
│  │ 消费告警队列          │  │ httplib::Server::listen()   │     │
│  │ AlgoMqttBus::publish │  │ POST /start /stop /status   │     │
│  │ → broker (QoS1)      │  │                              │     │
│  └─────────────────────┘  └──────────────────────────────┘     │
│                                                                │
│  ┌─────────────────────┐  ┌──────────────────────────────┐     │
│  │ 信号处理 (系统线程)    │  │ 主线程 (waitForShutdown)     │     │
│  │ Ctrl+C Handler       │  │ 10ms 轮询 s_exit 标志       │     │
│  └─────────────────────┘  └──────────────────────────────┘     │
└────────────────────────────────────────────────────────────────┘
```

> 说明：HTTP 心跳（→VIDEO）复用主线程或独立轻量线程发起，频率低（秒级），不与告警队列竞争；告警链路已全部由 MQTT publish 线程承担。

| 线程 | 数量 | 职责 |
|------|------|------|
| 主视频循环 | 1 | FFmpeg 解码 → 显示 → 推理提交 → 绘制 → 推流；顺带发起 HTTP 心跳→VIDEO |
| YOLO Worker | N (默认 3) | ONNX Runtime 并行推理 |
| MQTT publish | 1 | 消费告警队列，经 AlgoMqttBus 发布 QoS1 信封到 broker（→iot-sink） |
| HTTP 控制 | 1 | 运行时启停推流的 REST API（控制面，与告警链路分离） |
| 等待退出 | 1 | 10ms 轮询检测退出信号 |
| 信号处理 | 1 (系统) | Ctrl+C / SIGTERM 处理 |

---

## 七、配置文件格式

```ini
[video]
rtsp_url=rtsp://admin:password@192.168.1.64:554/Streaming/Channels/101
rtmp_url=rtmp://localhost:1935/live/stream_123
width=1920
height=1080
fps=15

[ai]
enable=true
model_path=F:/models/yolov11n.onnx
classes_path=F:/models/coco.names
threads=3

[alarm]
enable=true
# @Deprecated 告警已默认走 MQTT 总线（[mqtt] 段）；仅当 algo_bus.transport=http 时回退使用
hook_url=http://localhost:5000/api/alarm/callback/123
confidence_threshold=0.6
cooldown_time=30

[mqtt]
# 逗号分隔的 broker 列表，自动去 tcp:// / mqtt:// 前缀，缺省端口 1883
broker_urls=127.0.0.1:1883
# 鉴权（可被环境变量 MQTT_ALGO_USERNAME / MQTT_ALGO_PASSWORD / MQTT_ALGO_CLIENT_ID 覆盖）
username=algo
password=******
client_id=runtime-100001
tenant=default

[algo_bus]
# 总开关：默认空=MQTT；http / off / 0 / false / no = 关闭回退 HTTP
transport=

[compute]
node_id=node-local-01

[features]
enable_rtmp=true
enable_draw=true
enable_alarm=true

[task]
task_id=100001             # HTTP 控制端口 = 8000 + 100001 = 810001

[regions]
area_1=[[100,200],[500,200],[500,600],[100,600]]
area_2=[[600,100],[900,100],[900,400],[600,400]]
```

---

## 八、构建系统

### CMake 平台适配

```
CMakeLists.txt
    │
    ├── WIN32 分支:
    │     ├── 编译器: MSVC (/std:c++17 /utf-8)
    │     ├── 包管理: vcpkg (+ 硬编码路径指向 F:/EASYLOT/vcpkg-master)
    │     ├── ONNX: vcpkg 头文件 + Python DirectML DLL (混合方案)
    │     ├── 依赖: find_package(OpenCV/jsoncpp/glog/CURL)
    │     ├── FFmpeg: find_library(avcodec/avformat/avutil/swscale)
    │     ├── 排除: Yolov11ThreadPool.cpp (使用 Windows 版本)
    │     └── 链接: onnxruntime + ws2_32 + bcrypt
    │
    └── Linux 分支:
          ├── 编译器: g++ (-std=c++17 -pthread)
          ├── 依赖: find_library(jsoncpp/glog/inih/pugixml)
          ├── OpenCV: pkg-config opencv4 → 自动获取完整库列表
          ├── FFmpeg: pkg-config 或直接链接 avformat/avcodec/avutil/swscale
          ├── 排除: Yolov11ThreadPool_Windows.cpp
          └── 链接: onnxruntime + pthread + curl + crypto + ssl
```

### Windows 构建流程

```bat
build.bat:
  1. mkdir build && cd build
  2. cmake .. -G "Visual Studio 17 2022"
         -DCMAKE_TOOLCHAIN_FILE=F:/vcpkg/scripts/buildsystems/vcpkg.cmake
  3. cmake --build . --config Release
  4. copy conan DLLs + config
```

### 依赖矩阵

| 依赖 | Windows 来源 | Linux 来源 |
|------|-------------|-----------|
| OpenCV | vcpkg | apt/pkg-config |
| ONNX Runtime | vcpkg + Python DirectML | 系统安装 |
| FFmpeg | vcpkg | apt/pkg-config |
| jsoncpp | vcpkg | 系统库 |
| glog | vcpkg | 系统库 |
| CURL | vcpkg | 系统库 |
| cpp-httplib | 嵌入 (`3rdparty/`) | 嵌入 |

---

## 九、部署架构

```
                        ┌──────────────────────────────────┐
                        │  云中心 / 集群节点                  │
                        │  SRS/ZLM (流媒体, ai_rtmp 入口)    │
                        │  EMQX (MQTT broker, :1883)        │
                        │  iot-sink (订阅 mqtt/iot-*)       │
                        │  VIDEO (HTTP 心跳 :5000)          │
                        └──────────────────┬───────────────┘
                           RTMP 推流        │   MQTT publish 告警 / HTTP 心跳
                                           │
              ┌────────────────────────────┴────────────────────────┐
              │                                                      │
    ┌─────────┴──────────┐                            ┌─────────┴──────────┐
    │  工控机 (Windows)   │                            │  边缘盒子 (Linux)   │
    │  executor=cpp       │                            │  executor=cpp       │
    │  RUNTIME/TASK.exe   │                            │  RUNTIME/TASK (ELF) │
    │  DirectML (RTX5060) │                            │  ONNX CUDA          │
    │  RTSP ← 海康/大华   │                            │  RTSP ← 通用摄像头   │
    └─────────────────────┘                            └─────────────────────┘
```

> 告警链路：RUNTIME/TASK → MQTT broker → iot-sink（`IotAlgoBusMqttHandler` 订阅 `mqtt/iot-*`，落库后转发 Kafka 通知）。心跳链路：RUNTIME/TASK → HTTP POST → VIDEO `/video/algorithm/heartbeat/{realtime,patrol}`（非告警，保持 HTTP）。

---

## 十、数据流全链路

```
┌─────────────────────────────────────────────────────────────────────┐
│                   RUNTIME / TASK 数据流全链路                         │
│                                                                     │
│  摄像头                                                              │
│    │ RTSP (H.264/H.265)                                             │
│    ▼                                                                │
│  ┌──────────────────────────────────────────────────────┐           │
│  │  FFmpeg 解码                                          │           │
│  │  av_read_frame() → AVPacket → avcodec_send_packet()  │           │
│  │  → AVFrame (YUV420P) → sws_scale() → cv::Mat (BGR)  │           │
│  └──────────────────────┬───────────────────────────────┘           │
│                         │ frameId++                                 │
│            每 8 帧一次    │                                           │
│                         ▼                                           │
│  ┌──────────────────────────────────────────────────────┐           │
│  │  YOLOv11 推理 (多线程池)                               │           │
│  │  Mat(BGR) → resize(640×640) → normalize → NCHW       │           │
│  │  → ONNX Run() → [1,84,8400] → NMS → DetectObject[]  │           │
│  └──────────────────────┬───────────────────────────────┘           │
│                         │ objects[]                                 │
│           ┌─────────────┼─────────────┐                             │
│           ▼             ▼             ▼                             │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────┐                  │
│  │ Draw     │  │ Alarm Check  │  │ RTMP Push    │                  │
│  │ BBox 绘制│  │ 点在区域判断  │  │ H.264 编码   │                  │
│  │ 帧上叠加 │  │ 冷却检查     │  │ 推流到 ai_rtmp│                  │
│  └──────────┘  └──────┬───────┘  └──────────────┘                  │
│                       │ 触发告警                                    │
│                       ▼                                             │
│              ┌──────────────────┐                                   │
│              │ Alarm Queue      │                                   │
│              │ MAX_SIZE = 20    │                                   │
│              └────────┬─────────┘                                   │
│                       │                                             │
│                       ▼                                             │
│              ┌──────────────────────────────┐                       │
│              │ AlgoMqttBus (MQTT publish)    │                       │
│              │ envelope {version,msgId,      │                       │
│              │   msgType,tenant,ts,payload}  │                       │
│              │ QoS1 → broker :1883           │                       │
│              │   (逐个 broker 尝试, 等 PUBACK)│                       │
│              └────────────┬─────────────────┘                       │
│                           │                                         │
│                           ▼                                         │
│              ┌──────────────────────────────┐                       │
│              │ iot-sink (IotAlgoBusMqtt-     │                       │
│              │   Handler 订阅 mqtt/iot-*)    │                       │
│              │  → 落库 → Kafka 通知          │                       │
│              └──────────────────────────────┘                       │
│                                                                     │
│  (并行) HTTP 心跳 POST → VIDEO /video/algorithm/heartbeat/*         │
│         （非告警链路，保持 HTTP，频率低）                            │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 十一、多路摄像头部署模式

TASK 是一个**单实例单摄像头**的进程模型，多路摄像头通过多进程管理：

```
工控机 / 边缘服务器
├── TASK.exe config/cam1.ini  (PID=1001, ControlPort=810001)
├── TASK.exe config/cam2.ini  (PID=1002, ControlPort=810002)
├── TASK.exe config/cam3.ini  (PID=1003, ControlPort=810003)
└── ...
     │
     各进程独立: 独立 FFmpeg 解码器 / 独立 YOLO 引擎 / 独立 RTMP 推流
     GPU 共享: 多进程共享同一 GPU，通过 DirectML / CUDA 调度
     控制面: NODE Agent (:9100) 通过 HTTP 统一管理启停
```

---

## 十二、RUNTIME 与 TASK 的关系

EasyAIoT 在 EDGE 联邦边缘模块整体删除后，边缘算力由 **RUNTIME（C++）原子模式**承接。本节界定两个 C++ 模块与上下游的职责边界。

### 12.1 模块定位

- **RUNTIME** — EasyAIoT 的 C++ 帧执行器。单实例拉一路 RTSP → FFmpeg 解码 → 调用 TASK 做推理 → 把检测结果（带框）回推到 `ai_rtmp`（realtime 默认推带框检测流）→ 经 **AlgoMqttBus** 发布告警/抓拍/后处理 MQTT 信封给 iot-sink；同时以 HTTP 心跳汇报给 VIDEO。本机与集群计算节点均支持 `executor=cpp`。
- **TASK** — YOLO 推理引擎（本文档第五章 5.5–5.7、5.9–5.10 所述的 ONNX Runtime 封装、多线程推理池、RTMP 编码、Draw、Datatype）。RUNTIME 在生产部署中作为 TASK 的上层壳复用其推理与推流能力。

### 12.2 EDGE 移除后的三方职责表

| 模块 | 语言 | 职责 |
|------|------|------|
| **VIDEO** | Python | 任务编排 / 预览 / 任务管理 / **HTTP 心跳入口** / 启停指令下发 |
| **iot-sink** | Java | 订阅 `mqtt/iot-*` 算法总线，**事件落库 / 通知 enrichment / 媒体归档**（录像 `on_dvr` Hook、告警图从 NFS 读盘上传 MinIO） |
| **RUNTIME（+TASK）** | C++ | **高速执行后端**：拉流→解码→推理→推 ai_rtmp→**MQTT 告警 + HTTP 心跳** |

> 原独立的 Python `edge` 套件（`python -m edge run`、~512MB 运行时）已退场。本机部署与集群计算节点统一走 `executor=cpp`，由 RUNTIME 承担边缘执行职责。

### 12.3 部署形态

```
单机部署:  RUNTIME + iot-sink + VIDEO + EMQX + SRS 同机（executor=cpp, broker=127.0.0.1:1883）
集群部署:  计算节点跑 RUNTIME（executor=cpp），broker/iot-sink/VIDEO 在控制节点；
           告警经 MQTT 总线跨网络回传 iot-sink，心跳经 HTTP 回传 VIDEO
```

---

## 十三、关键设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| **推理引擎** | ONNX Runtime | 跨硬件 (CUDA/DirectML/CPU), 模型格式统一 |
| **推理频率** | 每 8 帧一次 | 平衡检测率和 GPU 负载, 15fps 下 ~1.9 次/秒 |
| **线程池隔离** | 每个 Worker 独立 Session | ONNX Session 非线程安全 |
| **结果获取** | 非阻塞 getTargetResultNonBlock | 主循环不等待推理, 保证显示流畅 |
| **告警上报** | MQTT 总线 (AlgoMqttBus) + 异步队列 + 独立线程 | 不阻塞主循环；解耦 RUNTIME 与下游（iot-sink 订阅）；QoS1 保证可达；取代原 HTTP POST 回调 |
| **MQTT 客户端** | 自实现 MQTT 3.1.1（裸 TCP socket，无外部 SDK） | 仅发布不订阅，零外部依赖，便于跨平台编译；broker 列表逐个重试容错 |
| **心跳链路** | 仍走 HTTP → VIDEO | 心跳频率低、与告警解耦；VIDEO 仍是任务编排/心跳入口，不引入 MQTT 依赖 |
| **推流控制** | 运行时 HTTP API | 不重启进程即可启停推流 |
| **配置格式** | INI + JSON 混合 | INI 可读性好 (运维), JSON 结构化 (区域定义) |
| **HTTP 库** | cpp-httplib 嵌入 | 零外部依赖, 单头文件, C++17 原生（仅用于控制面与心跳，告警已不走 HTTP） |
| **GPU 加速** | Windows DirectML / Linux CUDA | 跨平台 GPU 推理, RTX 5060 友好 |

---

## 十四、已知风险

| 风险 | 严重度 | 说明 |
|------|--------|------|
| **硬编码路径** | 🔴 高 | CMakeLists.txt 包含 `F:/EASYLOT/vcpkg-master` 和 `G:/anaconda` 绝对路径 |
| **无单元测试** | 🟡 中 | 无 gtest/cppunit；告警链路依赖 VIDEO / iot-sink 侧的 MQTT 订阅集成测试覆盖 |
| **内存安全** | 🟡 中 | 存在裸指针 (AVFormatContext*、AlgoMqttBus socket_), 未使用 RAII 完全封装 |
| **重复代码** | 🟢 低 | CMakeLists_Windows.txt 有重复 PROJECT(TASK) 块 |
| **错误处理** | 🟡 中 | 部分 FFmpeg/ONNX 错误码未完整处理 |
| **ONNX 环境空指针** | 🟡 中 | `Ort::Env onnxEnv{nullptr}`, 依赖 LoadModel 正确初始化 |
| **告警队列满** | 🟢 低 | MAX=20, 满时丢弃最旧告警 (有日志警告) |

---

## 十五、性能特征

| 指标 | 估计值 | 说明 |
|------|--------|------|
| **内存占用** | ~200-500MB | 模型 + FFmpeg 缓冲 + 帧队列 |
| **CPU 使用** | 中低 | 解码+推理 3 线程 |
| **GPU 显存** | ~500MB-1GB | YOLOv11n ONNX + DirectML/CUDA 上下文 |
| **推理延迟** | ~10-30ms | YOLOv11n, RTX 5060 / GTX 1060+ |
| **端到端延迟** | ~50-150ms | 解码+推理+编码+推流 |
| **支持分辨率** | 640×360 ~ 1920×1080 | 推理前 resize 到 640×640 |
| **推流码率** | ~2-5 Mbps | CRF=23, veryfast, 依赖场景复杂度 |

---

## 十六、修订记录

| 版本 | 日期 | 变更摘要 | 触发来源 |
|------|------|----------|----------|
| V9.18.0 | 2026-08-12 | 删除 EDGE 联邦边缘模块；存储后端 Ceph→NFS 共享媒体栈（/mnt/easyaiot-media）；告警上报 HTTP→MQTT 算法总线（AlgoMqttBus/IotAlgoBusMqttHandler）；本文档标题由 (TASK 模块) 改为 (RUNTIME / TASK 模块)；新增「十二、RUNTIME 与 TASK 的关系」章节、5.8 AlgoMqttBus 子节、RUNTIME 源文件树、`[mqtt]`/`[algo_bus]`/`[compute]` 配置段；原 5.8 AlarmCallback（HTTP POST→AI:5000）整节改写为 AlgoMqttBus（MQTT publish→iot-sink） | commits 847b3c85/f13c491d/3b7c7f5c/242f8f31/42945f3f/7c47d3b9 |

---

> **一句话总结:** RUNTIME / TASK 是 EasyAIot 用 C++17 打造的边缘 AI 帧执行器 + YOLO 推理引擎，RTSP 拉流 → ONNX Runtime YOLOv11 多线程推理 → H.264 RTMP 推流（ai_rtmp）→ **AlgoMqttBus 发布 MQTT 告警信封到 iot-sink**（心跳仍走 HTTP→VIDEO），单二进制、配置驱动、跨平台，是 EasyAIot 三模块架构中 **性能最极致** 的执行后端。
