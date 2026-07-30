# Edge Computing — C++ 层详细架构 (TASK 模块)

> 基于整体架构文件 V9.17.0 + TASK 模块源代码深入分析
> 代码规模: 14 源文件 / ~3,500 有效代码行 / C++17

---

## 一、总体定位

TASK 模块是 EasyAIot 的 **极致性能边缘推理引擎**，用 C++ 实现从 RTSP 拉流 → YOLO 推理 → RTMP 推流 → HTTP 告警的完整闭环，跨平台 (Windows/Linux)，面向工控机和边缘盒子部署。

```
┌─────────────────────────────────────────────────────────────────────┐
│                    TASK — C++ 边缘推理引擎                             │
│                    C++17 / ONNX Runtime / FFmpeg / OpenCV              │
│                                                                     │
│  RTSP 摄像头                                                         │
│      │                                                               │
│      ▼                                                               │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────┐               │
│  │ FFmpeg   │ →  │ YOLOv11      │ →  │ RTMPEncoder  │ → RTMP 推流   │
│  │ 拉流解码  │    │ ONNX 推理    │    │ H.264 编码   │               │
│  └──────────┘    │ (多线程池)   │    └──────────────┘               │
│                  └──────┬───────┘                                    │
│                         │ 检测结果                                   │
│                  ┌──────┴───────┐                                    │
│                  ▼              ▼                                    │
│           ┌──────────┐   ┌──────────────┐                           │
│           │ Draw     │   │ AlarmCallback│ → HTTP POST → AI 模块     │
│           │ 绘制标注  │   │ 告警回调     │                           │
│           └──────────┘   └──────────────┘                           │
│                                                                     │
│  HTTP 控制服务器 (:8000+taskId) ← 运行时动态启停推流                   │
└─────────────────────────────────────────────────────────────────────┘
```

| 对比维度 | TASK (C++) | EDGE (Python) |
|----------|-----------|---------------|
| **定位** | 极致性能边缘推理 | 轻量边缘运行时 |
| **内存** | ~200-500MB | ~512MB |
| **推理引擎** | ONNX Runtime (GPU/DirectML) | PyTorch/ONNX |
| **推理速度** | 最快 (原生 C++) | 中等 |
| **灵活性** | 编译后固定 | CLI 动态下发任务 |
| **部署** | 编译二进制 | `python -m edge run` |

---

## 二、技术栈

| 组件 | 版本/说明 | 用途 |
|------|----------|------|
| **语言标准** | C++17 | `std::atomic`, `std::unique_ptr`, `std::make_unique`, `std::optional` |
| **推理引擎** | ONNX Runtime (C++ API) | YOLOv11 ONNX 模型加载和推理 |
| **视频解码** | FFmpeg libav (C API) | RTSP 拉流、帧解码 (H.264/H.265) |
| **视频编码** | FFmpeg libavcodec/libavformat | H.264 编码 + RTMP 推流 |
| **图像处理** | OpenCV 4.x | 帧格式转换、图像绘制、颜色空间转换 |
| **HTTP 通信** | cpp-httplib (嵌入) | HTTP 服务器 (控制面) + HTTP 客户端 (告警回调) |
| **JSON 处理** | jsoncpp | 告警回调 JSON 构建和区域解析 |
| **日志** | glog (Google Log) | 结构化日志输出 |
| **构建** | CMake 3.5+ | 跨平台构建系统 |
| **包管理** | vcpkg (Windows) / apt (Linux) | 依赖管理 |

---

## 三、目录结构

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
│   ├── AlarmCallback.h                ← 告警回调声明 (72 行)
│   ├── AlarmCallback.cpp              ← HTTP 告警实现
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
| `rtmpUrl` | string | `[video]` | RTMP 推流目标地址 |
| `hookHttpUrl` | string | `[alarm]` | 告警 HTTP 回调 URL |
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

**ConfigParser 功能**:
- 自定义 INI 解析器 (无外部依赖)
- 支持 `[section]` 分段解析
- 布尔值: `true/false`, `1/0`, `yes/no`, `on/off`
- JSON 区域解析: `[[x1,y1],[x2,y2],[x3,y3]...]` → `vector<cv::Point>`
- Trim 空白、注释跳过、错误报告

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
  │     └── AlarmCallback(hookUrl)
  │
  ├── _init_http_client()
  │     └── httplib::Client 初始化
  │
  ├── _startAlarmSenderThread()         ← 告警队列消费者线程
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
        _sendAlarmCallback(objects, regionName)
            └── alarmQueue.push()       ← 入队 (MAX=20)
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

**告警队列系统 (生产者-消费者)**:
```
主线程 (生产者)                    告警发送线程 (消费者)
    │                                    │
    ├─ 检测到目标在告警区域              │
    ├─ _checkAlarmCooldown()            │
    ├─ _sendAlarmCallback()             │
    │   └─ lock(mutex)                  │
    │   └─ alarmQueue.push(data)        │
    │   └─ cv.notify_one()  ──────────→ ├─ cv.wait()
    │   └─ unlock(mutex)                ├─ alarmQueue.pop()
    │                                    ├─ build JSON payload
                                         ├─ httplib::Client::Post()
                                         └─ LOG result
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

### 5.8 AlarmCallback — HTTP 告警回调

```cpp
class AlarmCallback {
    std::string hookUrl_;         // http://host:port/api/alarm/callback/{taskId}
    std::string host_;            // 解析自 URL
    int port_;                    // 解析自 URL
    std::string path_;            // 解析自 URL
    httplib::Client* client_;     // HTTP 客户端
};
```

**JSON 告警格式**:
```json
{
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
```

**关键特性**:
- URL 解析: 从 `http://host:port/path` 分离 host/port/path
- JSON 构建: 使用 jsoncpp 构建结构化告警
- 连接测试: `testConnection()` 启动前验证可达性
- 生产者-消费者: 通过 Detech 的告警队列异步发送，不阻塞主循环

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
│                      TASK 线程模型 (6 类线程)                    │
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
│  │ 告警发送线程          │  │ HTTP 控制服务器线程           │     │
│  │ _alarmSenderThread   │  │ _controlServerThread        │     │
│  │ 消费告警队列          │  │ httplib::Server::listen()   │     │
│  │ HTTP POST 回调       │  │ POST /start /stop /status   │     │
│  └─────────────────────┘  └──────────────────────────────┘     │
│                                                                │
│  ┌─────────────────────┐  ┌──────────────────────────────┐     │
│  │ 信号处理 (系统线程)    │  │ 主线程 (waitForShutdown)     │     │
│  │ Ctrl+C Handler       │  │ 10ms 轮询 s_exit 标志       │     │
│  └─────────────────────┘  └──────────────────────────────┘     │
└────────────────────────────────────────────────────────────────┘
```

| 线程 | 数量 | 职责 |
|------|------|------|
| 主视频循环 | 1 | FFmpeg 解码 → 显示 → 推理提交 → 绘制 → 推流 |
| YOLO Worker | N (默认 3) | ONNX Runtime 并行推理 |
| 告警发送 | 1 | 消费告警队列，HTTP POST 回调 |
| HTTP 控制 | 1 | 运行时启停推流的 REST API |
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
hook_url=http://localhost:5000/api/alarm/callback/123
confidence_threshold=0.6
cooldown_time=30

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
                        ┌─────────────────────┐
                        │  云中心服务器          │
                        │  SRS/ZLM (流媒体)    │
                        │  AI (:5000)          │
                        └──────────┬──────────┘
                                   │
                          RTMP 推流 │ HTTP 告警回调
                                   │
              ┌────────────────────┴────────────────────┐
              │                                         │
    ┌─────────┴──────────┐                   ┌─────────┴──────────┐
    │  工控机 (Windows)   │                   │  边缘盒子 (Linux)   │
    │                     │                   │                     │
    │  TASK.exe           │                   │  TASK (ELF)         │
    │  DirectML (RTX5060) │                   │  ONNX CUDA          │
    │  RTSP ← 海康/大华   │                   │  RTSP ← 通用摄像头   │
    └─────────────────────┘                   └─────────────────────┘
```

---

## 十、数据流全链路

```
┌─────────────────────────────────────────────────────────────────────┐
│                        TASK 数据流全链路                              │
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
│  │  YOLOv11 推理 (Android 线程池)                         │           │
│  │  Mat(BGR) → resize(640×640) → normalize → NCHW       │           │
│  │  → ONNX Run() → [1,84,8400] → NMS → DetectObject[]  │           │
│  └──────────────────────┬───────────────────────────────┘           │
│                         │ objects[]                                 │
│           ┌─────────────┼─────────────┐                             │
│           ▼             ▼             ▼                             │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────┐                  │
│  │ Draw     │  │ Alarm Check  │  │ RTMP Push    │                  │
│  │ BBox 绘制│  │ 点在区域判断  │  │ H.264 编码   │                  │
│  │ 帧上叠加 │  │ 冷却检查     │  │ RTMP 推流    │                  │
│  └──────────┘  └──────┬───────┘  └──────────────┘                  │
│                       │ 触发告警                                    │
│                       ▼                                             │
│              ┌──────────────────┐                                   │
│              │ Alarm Queue      │                                   │
│              │ MAX_SIZE = 20    │                                   │
│              └────────┬─────────┘                                   │
│                       │                                             │
│                       ▼                                             │
│              ┌──────────────────┐                                   │
│              │ HTTP POST        │                                   │
│              │ JSON Payload     │                                   │
│              │ → AI (:5000)     │                                   │
│              └──────────────────┘                                   │
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

## 十二、关键设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| **推理引擎** | ONNX Runtime | 跨硬件 (CUDA/DirectML/CPU), 模型格式统一 |
| **推理频率** | 每 8 帧一次 | 平衡检测率和 GPU 负载, 15fps 下 ~1.9 次/秒 |
| **线程池隔离** | 每个 Worker 独立 Session | ONNX Session 非线程安全 |
| **结果获取** | 非阻塞 getTargetResultNonBlock | 主循环不等待推理, 保证显示流畅 |
| **告警发送** | 异步队列 + 独立线程 | 不阻塞主循环, 容忍网络延迟 |
| **推流控制** | 运行时 HTTP API | 不重启进程即可启停推流 |
| **配置格式** | INI + JSON 混合 | INI 可读性好 (运维), JSON 结构化 (区域定义) |
| **HTTP 库** | cpp-httplib 嵌入 | 零外部依赖, 单头文件, C++17 原生 |
| **GPU 加速** | Windows DirectML / Linux CUDA | 跨平台 GPU 推理, RTX 5060 友好 |

---

## 十三、已知风险

| 风险 | 严重度 | 说明 |
|------|--------|------|
| **硬编码路径** | 🔴 高 | CMakeLists.txt 包含 `F:/EASYLOT/vcpkg-master` 和 `G:/anaconda` 绝对路径 |
| **无单元测试** | 🟡 中 | 仅有 Python 告警接收测试, 无 gtest/cppunit |
| **内存安全** | 🟡 中 | 存在裸指针 (httplib::Client*, AVFormatContext*), 未使用 RAII 完全封装 |
| **重复代码** | 🟢 低 | CMakeLists_Windows.txt 有重复 PROJECT(TASK) 块 |
| **错误处理** | 🟡 中 | 部分 FFmpeg/ONNX 错误码未完整处理 |
| **ONNX 环境空指针** | 🟡 中 | `Ort::Env onnxEnv{nullptr}`, 依赖 LoadModel 正确初始化 |
| **告警队列满** | 🟢 低 | MAX=20, 满时丢弃最旧告警 (有日志警告) |

---

## 十四、性能特征

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

> **一句话总结:** TASK 是 EasyAIot 用 C++17 打造的边缘 AI 推理引擎，RTSP 拉流 → ONNX Runtime YOLOv11 多线程推理 → H.264 RTMP 推流 → HTTP JSON 告警回调，单二进制、配置驱动、跨平台，是云边端一体化架构中 **性能最极致** 的一环。
