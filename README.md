# EasyAIoT (Cloud-Edge-Device Integrated Collaborative Algorithm Application Platform)

[![Gitee star](https://gitee.com/volara/easyaiot/badge/star.svg?theme=gvp)](https://gitee.com/soaring-xiongkulu/easyaiot/stargazers)
[![Gitee fork](https://gitee.com/volara/easyaiot/badge/fork.svg?theme=gvp)](https://gitee.com/soaring-xiongkulu/easyaiot/members)

<p style="font-size: 16px; line-height: 1.8; color: #555; font-weight: 400; margin: 20px 0;">
My vision is for this system to be accessible worldwide, achieving truly zero barriers to AI. Everyone should experience the benefits of AI, not just a privileged few.
</p>

<div align="center">
    <img src=".image/logo.png" width="30%" height="30%" alt="EasyAIoT">
</div>

<h4 align="center" style="display: flex; justify-content: center; gap: 20px; flex-wrap: wrap; padding: 20px; font-weight: bold;">
  <a href="./README.md">English</a>
  <span style="display: flex; align-items: center; color: #666; font-weight: bold;">|</span>
  <a href="./README_zh.md">简体中文</a>
  <span style="display: flex; align-items: center; color: #666; font-weight: bold;">|</span>
  <a href="./README_zh_tw.md">繁體中文</a>
  <span style="display: flex; align-items: center; color: #666; font-weight: bold;">|</span>
  <a href="./README_ru.md">Русский</a>
  <span style="display: flex; align-items: center; color: #666; font-weight: bold;">|</span>
  <a href="./README_fr.md">Français</a>
  <span style="display: flex; align-items: center; color: #666; font-weight: bold;">|</span>
  <a href="./README_ko.md">한국어</a>
</h4>

## 📖 Project Overview

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
<strong>EasyAIoT</strong> (Easy AI Internet of Things) is a <strong>cloud-edge-device integrated intelligent algorithm application platform</strong> dedicated to deeply fusing artificial intelligence with the Internet of Things—enabling cameras, sensors, and edge compute to work together on site. From device onboarding and data collection to real-time visual analysis, intelligent assessment, and alert orchestration, the entire chain runs on a single software stack.
</p>

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
Many smart IoT projects hit the same wall at deployment: video systems, device platforms, and algorithm services live in silos—integration is costly, operations are fragmented, and scaling is painful. <strong>EasyAIoT resolves this with one platform</strong>—the same software deploys on a 4 GB edge box for single-point intelligence, on AI all-in-one cameras for floor-level coverage, or inside an enterprise full-stack appliance that packs IoT management, massive video access, and AI analysis into one box—no multiple versions to maintain, no repeated integration across heterogeneous systems.
</p>

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
The platform comprises eight core modules—<strong>WEB, APP, DEVICE, NODE, VIDEO, AI, TASK, and EDGE</strong>—with Java as the stable control foundation, Python for AI and networking, and C++ for high-performance compute tasks, each language playing to its strengths. On the capability side: GB28181 / ONVIF multi-protocol camera access, real-time and snapshot algorithm tasks, YOLO object detection and SAM zero-shot auto-annotation, face/plate recognition, orchestrable business post-processing, federated compute cluster scheduling, and <strong>Infinite Federated Edge Cluster mode</strong> (~512MB memory, Ceph with zero local disk at the edge, one-line command turns ordinary boards intelligent, sprawl compute and aggregate to the cloud), and MQTT / TCP / HTTP IoT device lifecycle management. On the experience side: the Web console and mobile App / mini-program are capability-aligned, so command centers and field inspections share the same business logic—handle incidents anytime, anywhere.
</p>

<p style="font-size: 14px; line-height: 1.8; color: #444; margin: 16px 0 8px 0;">
<strong>In one sentence:</strong> EasyAIoT = AI + IoT—interconnect everything while enabling intelligent vision and intelligent control for everything.
</p>

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
📄 For a more complete illustrated introduction, see <a href=".doc/项目介绍/EasyAIoT项目介绍 V2.0.pptx" style="color: #3498db; text-decoration: none; font-weight: 600;">EasyAIoT Project Introduction V2.0 (PPT)</a>.
</p>

## 🌟 Some Thoughts on the Project

### 📍 Project Positioning

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
EasyAIoT is a cloud-edge-device integrated intelligent IoT platform that focuses on the deep integration of AI and IoT. Through core capabilities such as algorithm task management, real-time stream analysis, and model service cluster inference, the platform achieves a complete closed-loop from device access to data collection, AI analysis, and intelligent decision-making, truly realizing interconnected everything and intelligent control of everything.
</p>

### 🎯 Three Tiers, One Platform

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
Many intelligent IoT projects stall at deployment: <strong>full features won't fit on small machines; to make them fit, you cut capabilities, split versions, and maintain multiple deployment packages.</strong> EasyAIoT resolves this with one platform—<strong>edge boxes for point intelligence, AI all-in-one cameras for on-wall analysis, AIoT full-stack all-in-ones for the complete stack in one box</strong>. Pick the tier that matches your field hardware; the same software runs from single-site pilots through floor coverage to full-stack delivery—no split versions.
</p>

| Tier | Typical hardware (examples) | Recommended RAM | What you can do | Verified |
| :-- | :-- | :--: | :-- | :--: |
| **mini** Edge Lite | <strong>Edge box</strong> (4 GB industrial PC, store security all-in-one, site gateway) | ≥ 4 GB | <strong>Intelligence at one point</strong>: camera access, real-time analysis, smart alerts, model inference—visual AI at lowest cost | ~2 GB used, ample headroom |
| **standard** Standard | <strong>AI all-in-one camera</strong> (smart camera terminal, AI surveillance camera with compute, multi-sensor AI analyzer) | ≥ 16 GB | <strong>Each camera is a smart node</strong>: multiple cameras on the wall cover a floor/campus; devices, rules, and compute orchestrated together | ~10 GB, stable with headroom |
| **full** Full (default) | <strong>AIoT full-stack all-in-one</strong> (enterprise full-stack control all-in-one, industry IoT full-stack host, cloud-edge-device smart platform all-in-one) | ≥ 20 GB | <strong>IoT + video + AI in one box</strong>: device management, massive access, intelligent analysis, command and judgment unified—full capabilities long-term | ~14 GB, full features with headroom |

<p style="font-size: 14px; line-height: 1.8; color: #444; margin: 16px 0 8px 0;">
<strong>Install tier selection and resource compliance (verified):</strong>
</p>

<div style="display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; margin: 12px 0;">
  <div style="flex: 1 1 22%; min-width: 160px; text-align: center;">
    <img src=".image/deploy-profile-menu.png" alt="Deploy tier selection" style="width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
    <p style="font-size: 12px; color: #888; margin: 8px 4px 0;">Pick one tier for your field hardware</p>
  </div>
  <div style="flex: 1 1 22%; min-width: 160px; text-align: center;">
    <img src=".image/deploy-profile-mini.png" alt="mini verified compliance" style="width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
    <p style="font-size: 12px; color: #888; margin: 8px 4px 0;"><strong>Edge box (mini)</strong>: ~2 GB verified—intelligence at one point</p>
  </div>
  <div style="flex: 1 1 22%; min-width: 160px; text-align: center;">
    <img src=".image/deploy-profile-standard.png" alt="standard verified compliance" style="width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
    <p style="font-size: 12px; color: #888; margin: 8px 4px 0;"><strong>AI all-in-one camera (standard)</strong>: ~10 GB verified—network coverage with headroom</p>
  </div>
  <div style="flex: 1 1 22%; min-width: 160px; text-align: center;">
    <img src=".image/deploy-profile-full.png" alt="full verified compliance" style="width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
    <p style="font-size: 12px; color: #888; margin: 8px 4px 0;"><strong>AIoT full-stack all-in-one (full)</strong>: ~14 GB verified—full stack ready for production</p>
  </div>
</div>

<div style="margin: 20px 0; padding: 18px 22px; border-radius: 10px; border: 1px solid rgba(52, 152, 219, 0.25); background: linear-gradient(120deg, #f0f7ff 0%, #ffffff 55%, #eef9f4 100%);">
  <p style="font-size: 16px; font-weight: 700; color: #1a5276; margin: 0 0 8px 0;">🚀 EasyAIoT Infinite Federated Edge Cluster</p>
  <p style="font-size: 14px; line-height: 1.8; color: #333; margin: 0;">
    About <strong>512MB</strong> memory, <strong>Ceph with zero local disk at the edge</strong> (alerts and business objects write to shared Ceph—no local business disk); sprawl compute deployment with <strong>one command</strong> that turns ordinary development boards intelligent, while alerts and events aggregate to the cloud.
    Complements the three full-stack profiles above—full-stack owns cloud-edge control and orchestration; EDGE nodes own lightweight on-site inference and infinite horizontal scale-out: “one control plane, edge anywhere.”
  </p>
</div>

#### 🧠 AI Capabilities

<ul style="font-size: 14px; line-height: 1.8; color: #444; margin: 10px 0;">
  <li><strong>Custom Platform Name &amp; Logo Across All Touchpoints</strong>: After deploying EasyAIoT on site, users should see <em>their</em> platform—not a generic product name. The monitoring dashboard includes a visual "Platform Branding" panel where administrators can rebrand in the UI: update the admin console name and logo (synced to the sidebar and browser tab); set an independent command-center title on the big screen; and customize the login page name, logo, form title, plus light/dark background images—all three touchpoints stay visually consistent, take effect immediately, and can be saved or reset with one click.
    <ul style="margin: 5px 0; padding-left: 20px;">
      <li><strong>For system integrators and solution providers</strong>: Eliminates front-end reskinning, custom development, and release cycles; switch branding quickly between PoC demos and production delivery, reuse one codebase across multiple customers, shorten payment cycles, and improve solution reuse</li>
      <li><strong>For government, campus, hospital, and other end users</strong>: Login page, command dashboard, and daily admin console all display their organization's name and identity—stronger ownership and credibility for leadership visits and internal rollout, meeting branding requirements for public-sector and large-enterprise IT projects</li>
      <li><strong>For private-deployment and operations teams</strong>: Configure on site the same day for acceptance—no waiting on development schedules; one-click restore after multi-customer demos or pilot phases, lowering switching and redeployment costs</li>
    </ul>
  </li>
  <li><strong>YOLO26 Next-Generation Object Detection</strong>: Built-in next-generation object detection, ready out of the box for real-time feed analysis and snapshot recognition. On the same hardware, connect more camera streams with faster response and fewer false alarms. Supports the full loop from data collection, annotation, and training to deployment and inference—helping users iteratively build custom detection models at lower cost and quickly cover common security and industrial scenarios such as hard hat compliance, unauthorized entry, and fire hazards, making "see accurately, compute fast, scale easily" the default capability</li>
  <li><strong>YOLO26 Human Pose Analysis</strong>: Builds on object detection with human keypoint and skeleton pose analysis, ready out of the box with the YOLO26 Pose model. Supports three input modes—images, videos, and real-time camera streams. Image mode outputs skeleton annotations and person counts synchronously; video mode uses async task processing with pollable progress and downloadable results; camera mode connects to RTSP/RTMP live streams and overlays pose results on relayed output for remote monitoring and behavior analysis. The model inference page offers one-click switching between "Pose Analysis" and "Object Detection" with adjustable confidence, seamlessly integrated with existing model management, history, and side-by-side preview. Suitable for construction site compliance, fitness form assessment, crowd gathering awareness, and other scenarios that require understanding human structure and motion—moving the platform from "boxing targets" to "understanding poses"</li>
  <li><strong>Multi-Protocol Camera Access Support</strong>: Comprehensive support for GB28181 and ONVIF, two mainstream video surveillance protocols, enabling standardized device access and management. GB28181, as China's national standard, perfectly adapts to mainstream domestic surveillance equipment; ONVIF, as an international universal standard, is widely compatible with global mainstream camera brands. Through dual-protocol support, the platform seamlessly integrates with existing surveillance systems, achieving plug-and-play device access, automatic discovery, and unified management, significantly reducing device access barriers, enhancing system compatibility and scalability, and providing a solid technical foundation for large-scale camera deployment. In addition, NVR batch scan, registration, and unified management across same-segment and cross-segment networks are supported, covering mainstream brands including Hikvision, Dahua, Huawei, Ezviz, and Xiaomi, with native-protocol subnet discovery, one-click registration, and batch channel import, further reducing the cost of large-scale surveillance device onboarding and operations</li>
  <li><strong>Real-Time Intercom & PTZ Remote Control</strong>: Breaks through traditional surveillance's "watch-only, can't act" limitation. Operators can conduct voice broadcasting and PTZ control on the same real-time preview screen—no system switching, no on-site presence required. Remotely communicate, guide evacuations, or stop violations, compressing response from "dispatch personnel" to "speak and reach instantly." PTZ control lets cameras pan, tilt, and zoom on demand—quickly aim at incident areas and magnify details during emergencies, forming an integrated on-site response loop of "see clearly, aim precisely, speak and reach." Fully compatible with GB28181 and ONVIF devices, leveraging existing surveillance assets without additional intercom hardware or third-party software, instantly upgrading deployed cameras with remote communication and flexible dispatch capabilities, significantly reducing system silos and monitoring costs</li>
  <li><strong>Orchestrable Algorithm Post-Processing</strong>: Breaks through the "detect but can't judge" bottleneck by adding an independent business judgment layer on top of object detection, transforming visual perception results into operable, accountable, and statistically trackable business events. Supports flexible per-task definition of scenario rules such as people counting, line-crossing, dwell timeout, area loitering, and multi-condition composite alerts—quickly adapting to differentiated needs in construction site safety supervision, campus security, and traffic control without repeatedly tuning models, forging general vision capabilities into field-ready management tools. Post-processing and real-time analysis run independently and in parallel—monitoring feeds continue smooth judgment while business logic scales elastically on demand; judgment results are automatically archived and drive precise alerts, significantly reducing false positives/negatives and manual review costs. Business users focus on rule expression while the platform handles distribution, execution, and scale—truly moving from "being able to see" to "judge clearly, control effectively, and put it to use"</li>
  <li><strong>Multi-Central-Node × Multi-Worker-Node Federated Cluster</strong>: Designed for cross-region, multi-datacenter, and cloud-edge collaborative deployments, the platform adopts an "N central nodes + N worker nodes" federated architecture—central nodes serve as the unified control plane while worker nodes act as the compute and media execution plane, building a horizontally scalable distributed scheduling system. Each central node manages its own worker cluster, supporting runtime distribution and one-click remote deployment of monitoring agents, distributed storage, streaming engines, FFmpeg transcoding, video analytics runtimes, and model inference/training workloads. Multiple central nodes can interconnect and synchronize; the cluster swimlane view intuitively presents "central—worker" topology and resource levels, with lane-level batch maintenance and component distribution. Algorithm tasks, auto-labeling pipelines, and stream relay workloads are intelligently scheduled by node role and GPU capability with elastic queue dispatch—enabling massive stream ingestion, high-concurrency inference, and distributed training to run together in one cluster, truly delivering "onboard easily, schedule clearly, scale openly, govern completely"</li>
  <li><strong>SAM Zero-Start Auto-Labeling Orchestration Pipeline</strong>: Built for cold-start scenarios with no annotated samples and no usable detection model, the platform integrates SAM open-vocabulary segmentation with an intelligent orchestration engine to deliver a one-click, unattended labeling pipeline. Per strategy, the system automatically chains camera frame extraction, SAM text-prompt bootstrap labeling, YOLO fine-tuning once thresholds are met, production-phase YOLO high-speed inference with intelligent SAM fallback for missed detections, periodic iterative training, and automatic dataset packaging and export—closing the full "capture-annotate-train-export" loop. The orchestration hub continuously tracks pipeline phase and labeling progress, autonomously deciding among SAM, YOLO, and hybrid supplement modes and when to trigger training; supports pause/resume and elastic scheduling on local or cluster compute queues. With visual strategy configuration and run logs, users can grow a custom detection capability from zero samples and zero models, making "define categories in words, watch the model take shape" the default path for dataset building</li>
  <li><strong>Ten-Thousand-Node Elastic Compute Cluster & Horizontal Scaling Pool</strong>: Built for hyperscale AI and video workloads, the platform provides a cloud-edge-end distributed compute foundation that unifies algorithm tasks, stream relay, algorithm services, model training, and inference under one horizontal load-balancing and elastic scaling fabric. New servers join the fleet with one-click onboarding and immediately become schedulable compute units—the control plane automatically dispatches work and balances load based on resource levels and business pressure, enabling linear scaling from hundreds to tens of thousands of camera streams and from a single machine to ten-thousand-node clusters without redeployment or manual tuning. Massive stream ingestion, high-concurrency inference, and distributed training run together in a shared compute pool—truly delivering "scale on demand, run reliably, govern with confidence"</li>
  <li><strong>Infinite Federated Edge Cluster (EDGE)</strong>: For ordinary development boards such as RK3588 and Raspberry Pi and on-site compute nodes, delivers a lightweight headless edge algorithm runtime at about <strong>512MB</strong> memory—<strong>one command</strong> writes the control-plane address and starts, turning ordinary boards intelligent; sprawl compute deployment by site while alerts and events aggregate to the cloud. Alert images and snapshots write to shared Ceph and are archived by the center sink—<strong>Ceph with zero local disk at the edge</strong> for business storage (no local business disk, no direct MinIO upload). Extracts the algorithm execution plane from the VIDEO control plane into a dedicated <code>EDGE</code> module—no WEB UI, no local business database, focused on "receive commands, run inference, report events." On site configure only <code>EDGE_NODE_URL</code>; via enroll / runtime-config, auto-provision EMQX cluster endpoints, MQTT credentials, Ceph hot-buffer paths, and Topic contracts. Any number of nodes share one EMQX bus; the control plane schedules realtime / snapshot / patrol algorithm tasks by node capability. Start/stop commands arrive on <code>mqtt/iot-algo-task-cmd</code>; heartbeat, ack, alert, and postprocess flow back on the same bus—truly "512MB to start, Ceph zero local disk at the edge, one command online, infinite federated scale-out, on-site intelligence and cloud aggregation together"</li>
  <li><strong>Tianditu Spatial Visualization & Map-Based Analysis</strong>: Integrated with China's national Tianditu map service, the platform brings cameras, alerts, and person/vehicle recognition onto a single map—upgrading surveillance from "watching feeds" to "seeing the big picture." Both the streaming media and alert modules offer a "Map Distribution" view with a device directory tree for regional focus, giving instant visibility into checkpoint layout and online status. Map click-to-pin, location search, and batch coordinate import help GB channels, NVR channels, and direct-connect cameras get mapped quickly so every feed has clear spatial context. Alerts are automatically placed on the map via linked camera coordinates; filter by time, event type, task, and business tags, then open snapshots and recordings in one click—helping operators move fast from "where did it happen" to action. Combined with face and plate libraries, hits across multiple sites can be woven into spatial trails—<strong>trace by person</strong> to reconstruct movement and presence within a monitored area; <strong>trace by vehicle</strong> to link passing records and pinpoint routes and stop zones for find-person/find-vehicle, patrol deployment, and post-incident review. Mobile devices also support track playback to replay patrol and travel paths on a timeline. Switch freely between vector and satellite basemaps with auto-fit view, so managers use the map as the anchor to spot anomalies, lock onto targets, and coordinate response faster</li>
  <li><strong>Qwen / DeepSeek Multi-GPU Deployment</strong>: Supports deploying Qwen, DeepSeek, and other large language models across multiple GPUs in parallel. GPU resources can be scheduled flexibly at the cluster and Worker level, enabling elastic scaling and load balancing of model instances to deliver stable inference under high concurrency and long-context workloads</li>
  <li><strong>Vision Large Model Intelligent Understanding</strong>: Integrated with QwenVL3 vision large model, supports deep visual reasoning and semantic understanding of real-time video frames, enabling intelligent analysis and scene comprehension of frame content, providing richer visual cognitive capabilities, achieving a leap from pixel-level perception to semantic-level understanding</li>
  <li><strong>Real-Time Camera Feed AI Analysis</strong>: For RTSP/RTMP real-time video streams, builds a full-chain analysis pipeline of "stream pull & decode → intelligent frame extraction → model inference → structured output → alert linkage", converting frame changes into searchable, analyzable structured detection events with millisecond response. Viewing chain and algorithm chain are architecturally decoupled, with tiered bitrates and multi-GPU collaborative scheduling balanced together, balancing preview clarity and high-concurrency throughput. Analysis results seamlessly connect with detection regions, defense time periods, face/plate recognition, and orchestrable post-processing rules, upgrading the traditional "human staring at screens, reviewing after the fact" duty model to "machines monitor 24/7, anomalies pushed in seconds, evidence auto-archived", turning real-time video from passive viewing into infrastructure for active perception and intelligent judgment</li>
  <li><strong>Intelligent Camera Patrol</strong>: Designed for monitoring scenarios with many camera streams but limited staffing, the platform provides split-screen patrol and device-directory batch patrol capabilities, performing rotational AI analysis across large-scale camera fleets under limited concurrent connections. Supports three scheduling modes—rotation, connection pool, and hybrid—automatically capturing frames at set intervals, running detection models, and linking alerts with face/plate recognition. In hybrid mode, focus streams stay permanently monitored while background streams rotate via pooled connections, balancing priority surveillance and full-area coverage. Patrol progress is pushed in real time, captured frames are automatically archived, and patrol sessions for hundreds of cameras can be launched in one click from split-screen views or device directories—upgrading traditional manual screen-by-screen monitoring to intelligent automated patrol with "fewer connections, broader coverage, faster discovery"</li>
  <li><strong>Cloud-Edge-Device Integrated Algorithm Alert Monitoring Dashboard</strong>: Provides a unified cloud-edge-device integrated algorithm alert monitoring dashboard that displays key information in real-time, including device status, algorithm task operations, alarm event statistics, and video stream analysis results. Supports multi-dimensional data visualization, achieving unified monitoring and management of cloud, edge, and device layers, providing decision-makers with a global perspective intelligent monitoring command center</li>
  <li><strong>Face Recognition and Face Library Management</strong>: Supports flexibly enabling face recognition in camera tasks. Built on Milvus for face library and facial feature vector management, it provides create/query/update/delete capabilities for face samples and feature vectors, as well as high-performance vector retrieval. It supports efficient face comparison and identity retrieval on captured frames, while fully recording match results, snapshots, camera location information, and device context for personnel trajectory tracing, security forensics, and multidimensional statistical analysis.</li>
  <li><strong>License Plate Recognition and Plate Library Management</strong>: Enable license plate recognition in monitoring tasks with one click. Automatically reads plate information from passing vehicles and compares against your own plate libraries in real time. Flexibly maintain whitelists, blacklists, and business tags; trigger instant alerts when vehicles match rules—supporting access control at entrances and exits, targeted vehicle watchlists, and visitor vs. registered vehicle management. Automatically registers newly seen plates and keeps complete capture and match records for post-incident lookups, trace verification, and evidence retention. Recognition runs in parallel with existing video analytics without affecting monitoring and alert stability or real-time performance</li>
  <li><strong>Device Detection Region Drawing</strong>: Provides a visual device detection region drawing tool that supports drawing rectangular and polygonal detection regions on device snapshot images, supports flexible association configuration between regions and algorithm models, supports visual management, editing, and deletion of regions, supports keyboard shortcuts to improve drawing efficiency, enabling precise region detection configuration and providing accurate detection range definitions for algorithm tasks</li>
  <li><strong>Intelligent Linked Alert Mechanism</strong>: Supports a triple-link mechanism between detection regions, defense time periods, and event alerts. The system intelligently determines whether a detected event simultaneously meets the specified detection region range, falls within the defense time period, and matches the alert event type. Alerts are only triggered when all three conditions are met, achieving precise spatiotemporal condition filtering, significantly reducing false positive rates, and improving the accuracy and practicality of the alert system</li>
  <li><strong>Large-Scale Camera Management</strong>: Supports access to hundreds of cameras, providing end-to-end services including collection, annotation, training, inference, export, analysis, alerting, recording, storage, and deployment</li>
  <li><strong>Algorithm Task Management</strong>: Supports creation and management of two types of algorithm tasks, each task can flexibly bind frame extractors and sorters to achieve precise video frame extraction and result sorting
    <ul style="margin: 5px 0; padding-left: 20px;">
      <li><strong>Real-Time Algorithm Tasks</strong>: Used for real-time video analysis, supporting RTSP/RTMP stream real-time processing with millisecond-level response capabilities, suitable for monitoring, security, and other real-time scenarios</li>
      <li><strong>Snapshot Algorithm Tasks</strong>: Used for snapshot image analysis, performing intelligent recognition and analysis on captured images, suitable for event backtracking, image retrieval, and other scenarios</li>
    </ul>
  </li>
  <li><strong>Dataset Annotation and Multi-Format Dataset Management</strong>: Provides a visual image annotation workspace supporting rectangle and polygon labeling, category management, and progress tracking; fully supports flexible import and export of mainstream dataset formats including YOLO, COCO, and ImageFolder, with cloud platform dataset integration enabling one-click import and synchronized export of cloud-hosted datasets—seamlessly connecting data collection, annotation, training, and deployment across the full pipeline</li>
  <li><strong>Stream Forwarding</strong>: Supports direct viewing of camera real-time feeds without enabling AI analysis functionality. By creating stream forwarding tasks, multiple cameras can be batch-pushed, enabling synchronous viewing of multiple video streams to meet pure video monitoring scenario requirements</li>
  <li><strong>GPU Discovery, Load-Aware Allocation, and Multi-GPU Collaboration</strong>: The platform provides GPU resource discovery and intelligent scheduling: it detects the number of available GPUs and dynamically assigns video encode/decode and algorithm inference work across cards according to per-GPU load, running tasks in parallel where appropriate to raise multi-stream throughput and utilization while keeping the pipeline stable—coordinating frame processing and model inference in multi-GPU deployments</li>
  <li><strong>Smart Transport Selection and Resilient Stream Pull</strong>: On RTSP and similar pull paths, the system can evaluate URL/path and related signals to choose and switch transport-layer modes; camera pulls default to UDP for lower latency. When consecutive frames indicate gray screen, decode errors, or stream collapse (decode stall), RTSP reconnect and link recovery run automatically to limit prolonged artifacts or frozen video</li>
  <li><strong>Separate Viewing vs Algorithm Pipelines and Tiered Bitrates</strong>: Live preview and wall viewing are decoupled from algorithm analysis frame extraction in both data path and control policy, with two independent control planes. The viewing path uses about 6500 Kbps to prioritize sharp, smooth monitoring; the algorithm path uses about 3500 Kbps to balance detection quality with compute and bandwidth, avoiding analysis and viewing competing on one high-bitrate channel—so operators get clear, fluid video while analysis stays scalable</li>
  <li><strong>Model Service Cluster Inference</strong>: Supports distributed model inference service clusters, achieving intelligent load balancing, automatic failover, and high availability guarantees, significantly improving inference throughput and system stability</li>
  <li><strong>Defense Time Period Management</strong>: Supports two defense strategies: full defense mode and half defense mode, allowing flexible configuration of defense rules for different time periods, achieving precise time-based intelligent monitoring and alerting</li>
  <li><strong>OCR and Speech Recognition</strong>: High-precision text recognition based on PaddleOCR with speech-to-text functionality, providing multi-language recognition capabilities</li>
  <li><strong>Multimodal Vision Large Models</strong>: Supports various vision tasks including object recognition and text recognition, providing powerful image understanding and scene analysis capabilities</li>
  <li><strong>LLM Large Language Models</strong>: Supports intelligent analysis and understanding of multiple input formats including RTSP streams, video, images, audio, and text, achieving multimodal content understanding</li>
  <li><strong>Model Deployment and Version Management</strong>: Supports rapid deployment and version management of AI models, enabling one-click model deployment, version rollback, and gray release</li>
  <li><strong>Multi-Instance Management</strong>: Supports concurrent operation and resource scheduling of multiple model instances, improving system utilization and resource efficiency</li>
  <li><strong>Camera Snapshot</strong>: Supports real-time camera snapshot functionality with configurable snapshot rules and trigger conditions, achieving intelligent snapshot capture and event recording</li>
  <li><strong>Snapshot Storage Space Management</strong>: Provides storage space management for snapshot images with quota and cleanup policy support, ensuring rational utilization of storage resources</li>
  <li><strong>Video Storage Space Management</strong>: Provides storage space management for video files with automatic cleanup and archiving, achieving intelligent storage resource management</li>
  <li><strong>Snapshot Image Management</strong>: Supports full lifecycle management of snapshot images including viewing, searching, downloading, and deletion, providing convenient image management functionality</li>
  <li><strong>Device Directory Management</strong>: Provides hierarchical device directory management with device grouping, multi-level management, and permission control, achieving organized and fine-grained device management</li>
  <li><strong>Alarm Recording</strong>: Supports automatic recording triggered by alarm events. When abnormal events are detected, relevant video clips are automatically recorded, providing a complete alarm evidence chain. Supports viewing, downloading, and management of alarm recordings</li>
  <li><strong>Alarm Events</strong>: Provides comprehensive alarm event management functionality, supporting real-time alarm event push, historical query, statistical analysis, event processing, and status tracking, achieving full lifecycle management of alarms</li>
  <li><strong>Video Playback</strong>: Supports fast retrieval and playback of historical recordings, providing convenient operations such as timeline positioning, variable speed playback, and keyframe jumping. Supports synchronized playback of multiple video streams, meeting event backtracking and analysis needs</li>
</ul>

#### 🌐 IoT Capabilities

<ul style="font-size: 14px; line-height: 1.8; color: #444; margin: 10px 0;">
  <li><strong>Device Access and Management</strong>: Device registration, authentication, status monitoring, lifecycle management</li>
  <li><strong>Product and Thing Model Management</strong>: Product definition, thing model configuration, product management</li>
  <li><strong>Multi-Protocol Support</strong>: Multiple IoT protocols including MQTT, TCP, HTTP</li>
  <li><strong>Device Authentication and Dynamic Registration</strong>: Secure access, identity authentication, dynamic device registration</li>
  <li><strong>Rule Engine</strong>: Data flow rules, message routing, data transformation</li>
  <li><strong>Data Collection and Storage</strong>: Device data collection, storage, query, and analysis</li>
  <li><strong>Device Status Monitoring and Alert Management</strong>: Real-time monitoring, anomaly alerts, intelligent decision-making</li>
  <li><strong>Notification Management</strong>: Supports 7 notification methods including Feishu, DingTalk, Enterprise WeChat, Email, Tencent Cloud SMS, Alibaba Cloud SMS, and Webhook, enabling flexible and multi-channel alert notifications</li>
</ul>

#### 📱 Mobile APP

<ul style="font-size: 14px; line-height: 1.8; color: #444; margin: 10px 0;">
  <li><strong>Multi-Channel Access</strong>: Available on phones, mini programs, and apps—ops and management are no longer tied to a desk; handle issues on-site in real time</li>
  <li><strong>Capability Parity</strong>: Mobile matches the PC admin console feature-for-feature; switch devices without losing control</li>
  <li><strong>Device Management</strong>: Unified management across access methods; browse channels at a glance and tap for live view—stay informed during field inspections</li>
  <li><strong>Stream Forwarding</strong>: Create and stop forwarding tasks anytime; monitor cluster nodes and stream status—schedule video resources remotely</li>
  <li><strong>Algorithm Tasks</strong>: Start and stop real-time and snapshot tasks on the go; track detection results without waiting to get back to the office</li>
  <li><strong>Alert Center</strong>: Search alerts instantly; tap to view snapshots and recordings—verify and follow up while on mobile duty</li>
  <li><strong>Model Management</strong>: Deployment status at a glance; always know what's live</li>
  <li><strong>Model Inference</strong>: Upload an image on-site and get results immediately—spot checks without returning to PC</li>
  <li><strong>Model Training</strong>: Monitor training progress anytime; stop remotely when needed to avoid wasted compute</li>
  <li><strong>Personal Center</strong>: Account, tenant, and app preferences in one place—convenient across devices</li>
  <li><strong>Smooth Viewing</strong>: Live feeds and alarm recordings play smoothly on mobile—low latency, no stutter, uncompromised duty experience</li>
  <li><strong>Stay Connected</strong>: Sessions stay active with less re-login—bringing cloud-edge-device intelligent control to phones and mini programs</li>
</ul>

### 📦 Built-in AI Models

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
The platform is ready to use out of the box, with multiple pre-trained models built in for security monitoring, industrial sites, smart transportation, and similar scenarios. Select them directly in algorithm tasks for rapid deployment and inference—no training from scratch required to cover common vision detection needs.
</p>

| Model Name | Inference Format | Base Model | Capability |
| :-- | :--: | :--: | :-- |
| Safety Helmet Model | ONNX | YOLOv8 | Detect whether workers are wearing safety helmets |
| Sleeping on Duty Model | PyTorch | YOLOv8 | Detect sleeping on duty, leaving post, and other abnormal behaviors |
| Person Detection Model | PyTorch | YOLOv8 | General human detection for identifying and locating people in the frame |
| License Plate Model | ONNX | YOLOv8 | Recognize vehicle license plate information |
| Reflective Vest Model | PyTorch | YOLOv8 | Detect whether workers are wearing reflective vests |
| Flame Model | PyTorch | YOLOv8 | Detect open flames and fire hazards |
| Smoking Detection Model | PyTorch | YOLOv8 | Detect smoking behavior |
| Phone Call Detection Model | ONNX | YOLOv8 | Detect phone calls and mobile phone use |
| Road Waterlogging Model | ONNX | YOLOv8 | Detect road water accumulation and surface flooding |
| Face Mask Model | ONNX | YOLOv8 | Detect whether people are wearing masks correctly |
| Fall Detection Model | ONNX | YOLOv8 | Detect falls and other abnormal postures |
| Face Detection Model | ONNX | YOLOv8 | Detect face locations in the frame to support face recognition workflows |

### 💡 Technical Philosophy

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
We believe no single programming language excels at everything, but through the deep integration of three programming languages, EasyAIoT leverages the strengths of each to build a powerful technical ecosystem.
</p>

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
Java excels at building stable and reliable platform architectures but is not suitable for network programming and AI development; Python excels at network programming and AI algorithm development but has bottlenecks in high-performance task execution; C++ excels at high-performance task execution but is less suitable than the other two for platform development and AI programming. EasyAIoT adopts a tri-lingual mixed programming architecture, fully leveraging the strengths of each language to build an AIoT platform that's challenging to implement but extremely easy to use.
</p>

![EasyAIoT Platform Architecture.jpg](.image/iframe2.jpg)

### 🔄 Module Data Flow

<img src=".image/iframe3.jpg" alt="EasyAIoT Platform Architecture" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">

### 🤖 Zero-Shot Labeling Technology

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
Innovatively leveraging large models to construct a zero-shot labeling technical system (ideally completely eliminating manual labeling, achieving full automation of the labeling process), this technology generates initial data through large models and completes automatic labeling via prompt engineering. It then ensures data quality through optional human-machine collaborative verification, thereby training an initial small model. This small model, through continuous iteration and self-optimization, achieves co-evolution of labeling efficiency and model accuracy, ultimately driving continuous improvement in system performance.
</p>

<img src=".image/iframe4.jpg" alt="EasyAIoT Platform Architecture" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">

### 🏗️ Project Architecture Features

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
EasyAIoT is not actually one project; it is eight distinct projects.
</p>

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
What's the benefit? Suppose you are on a resource-constrained device (like an RK3588). You can extract and independently deploy just one of those projects. Therefore, while this project appears to be a cloud platform, it simultaneously functions as an edge platform.
</p>

<div style="margin: 30px 0; padding: 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 10px; color: white;">

<p style="font-size: 16px; line-height: 1.8; margin: 0; font-weight: 500;">
🌟 Genuine open source is rare. If you find this project useful, please star it before leaving - your support means everything to us!<br>
<small style="font-size: 14px; opacity: 0.9;">(In an era where fake open-source projects are rampant, this project stands out as an exception.)</small>
</p>

</div>

### 🌍 Localization Support

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
EasyAIoT actively responds to localization strategies, providing comprehensive support for localized hardware and operating systems, delivering secure and controllable AIoT solutions for users:
</p>

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; margin: 20px 0;">

<div style="padding: 20px; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🖥️ Server-Side Support</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>Full compatibility with Hygon x86 architecture processors</li>
  <li>Support for localized server hardware platforms</li>
  <li>Targeted performance optimization solutions</li>
  <li>Ensures stable operation of enterprise applications</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">📱 Edge-Side Support</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>Infinite federated edge cluster: join with ~512MB memory</li>
  <li>Ceph: zero local disk footprint—business objects never land on local disks</li>
  <li>One command turns RK3588 and similar boards intelligent</li>
  <li>Sprawl compute by site; alerts and events aggregate to the cloud</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🖱️ Operating System Support</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>Compatible with Kylin operating system</li>
  <li>Support for localized Linux distributions like Founder</li>
  <li>Adaptation to mainstream localized operating systems like UOS</li>
  <li>Provides complete localized deployment solutions</li>
</ul>
</div>

</div>

## 🎯 Application Scenarios

![Application Scenarios.png](.image/适用场景.png)

## 🧩 Project Structure

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
EasyAIoT consists of eight core projects:
</p>

<table style="width: 100%; border-collapse: collapse; margin: 20px 0; font-size: 14px;">
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; background-color: #f8f9fa; font-weight: 600; color: #2c3e50; width: 20%;">Module</td>
<td style="padding: 15px; border: 1px solid #e0e0e0; background-color: #f8f9fa; font-weight: 600; color: #2c3e50;">Description</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>WEB Module</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">Frontend management interface based on Vue, providing a unified user interaction experience</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>APP Module</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">
  <ul style="margin: 5px 0; padding-left: 20px;">
    <li><strong>Multi-Channel Access</strong>: One build, multiple touchpoints—phones, mini programs, and apps</li>
    <li><strong>Capability Parity</strong>: Matches PC admin console capabilities with multi-tenant switching</li>
    <li><strong>Device Management</strong>: Unified management for direct cameras, GB28181, and NVR; online status and channel browsing with one-tap live preview in device details</li>
    <li><strong>Stream Forwarding</strong>: Task creation, start/stop, cluster node status, and multi-stream URL viewing</li>
    <li><strong>Algorithm Tasks</strong>: Real-time/snapshot algorithm task list, start/stop control, and detection/frame stats</li>
    <li><strong>Alert Center</strong>: Alert search, snapshot preview, and alarm recording VOD playback</li>
    <li><strong>Models & AI</strong>: Model list and deployment status, mobile image inference workbench, training task progress monitoring and stop</li>
    <li><strong>Profile</strong>: Personal info, account security, FAQ, feedback, and app settings</li>
  </ul>
</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>DEVICE Module</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">
  <ul style="margin: 5px 0; padding-left: 20px;">
    <li><strong>Technical Advantages</strong>: Based on JDK21, providing better performance and modern features</li>
    <li><strong>Device Management</strong>: Device registration, authentication, status monitoring, lifecycle management</li>
    <li><strong>Product Management</strong>: Product definition, thing model management, product configuration</li>
    <li><strong>Protocol Support</strong>: Multiple IoT protocols including MQTT, TCP, HTTP</li>
    <li><strong>Device Authentication</strong>: Device dynamic registration, identity authentication, secure access</li>
    <li><strong>Rule Engine</strong>: Data flow rules, message routing, data transformation</li>
    <li><strong>Data Collection</strong>: Device data collection, storage, query, and analysis</li>
    <li><strong>Node Control Plane</strong>: Built-in <code>iot-node</code> microservice providing unified control plane for compute/media node CRUD, SSH connectivity testing, Agent registration and heartbeat, workload scheduling, and media node pool allocation</li>
  </ul>
</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>NODE Module</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">
  <ul style="margin: 5px 0; padding-left: 20px;">
    <li><strong>Node Agent</strong>: Python-based edge/remote node Agent; one-click install via <code>install.sh</code> as a systemd service, automatically joining the platform when deployed on target servers</li>
    <li><strong>Control Plane Communication</strong>: Registers with the <code>iot-node</code> control plane and sends periodic heartbeats, reporting CPU, memory, disk, GPU utilization, and active workload status in real time</li>
    <li><strong>Remote Workloads</strong>: Receives deploy/stop commands from the control plane via HTTP API (default port 9100), launching AI model services, algorithm tasks, FFmpeg transcoding, and other workloads locally on the node</li>
    <li><strong>Media Node Pool</strong>: Supports remote <code>docker compose</code> deployment of SRS/ZLM streaming stacks on nodes, working with the control plane for sticky device-to-media-node binding and stream URL generation</li>
    <li><strong>Node Roles</strong>: Supports compute, media, and hybrid roles, enabling cross-node scheduling and elastic scaling for AI inference, algorithm tasks, and streaming services</li>
    <li><strong>Offline-Friendly</strong>: Provides pip wheels offline dependency bundling and Agent hot-update capabilities, suitable for batch node onboarding in air-gapped or restricted network environments</li>
  </ul>
</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>VIDEO Module</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">
  <ul style="margin: 5px 0; padding-left: 20px;">
    <li><strong>Stream Processing</strong>: Supports RTSP/RTMP stream real-time processing and transmission</li>
    <li><strong>Algorithm Task Management</strong>: Supports real-time algorithm tasks and snapshot algorithm tasks, used for real-time video analysis and snapshot image analysis respectively</li>
    <li><strong>Frame Extractor and Sorter</strong>: Supports flexible frame extraction strategies and result sorting mechanisms, each algorithm task can bind independent frame extractors and sorters</li>
    <li><strong>Defense Time Period</strong>: Supports time-based configuration for full defense mode and half defense mode</li>
  </ul>
</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>AI Module</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">
  <ul style="margin: 5px 0; padding-left: 20px;">
    <li><strong>Intelligent Analysis</strong>: Responsible for video analysis and AI algorithm execution</li>
    <li><strong>Model Service Cluster</strong>: Supports distributed model inference services, achieving load balancing and high availability</li>
    <li><strong>Real-Time Inference</strong>: Provides millisecond-level response real-time intelligent analysis capabilities</li>
    <li><strong>Model Management</strong>: Supports model deployment, version management, and multi-instance scheduling</li>
  </ul>
</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>TASK Module</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">C++-based high-performance task processing module responsible for compute-intensive task execution</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>EDGE Module</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">
  <ul style="margin: 5px 0; padding-left: 20px;">
    <li><strong>Infinite Federated Edge Cluster Mode</strong>: The eighth core module—~512MB memory; Ceph zero local disk at the edge; one command turns ordinary boards intelligent; sprawl compute deployment with alerts and events aggregating to the cloud; infinite federated scale-out</li>
    <li><strong>Headless Edge Algorithm Runtime</strong>: Algorithm execution surface extracted from VIDEO; pure CLI / systemd with no WEB UI and no local business database, focused on edge inference and event reporting</li>
    <li><strong>Single Config Entry</strong>: On site configure only <code>EDGE_NODE_URL</code> (iot-node control-plane address); via <code>/admin-api/node/edge/enroll</code> and <code>runtime-config</code>, automatically receive MQTT broker list, tenant credentials, clientId, Ceph hot-buffer paths, and algorithm Topic contracts</li>
    <li><strong>All-MQTT Bus</strong>: Subscribes to <code>mqtt/iot-algo-task-cmd</code> for start/stop commands (filtered by <code>targetNodeId</code>); publishes heartbeat / ack / alert / postprocess; no direct Kafka and no HTTP management plane</li>
    <li><strong>Ceph: Zero Local Disk at the Edge</strong>: Alert images and snapshots write to shared Ceph paths; center-side <code>iot-sink</code> archives to MinIO—no local business disk at the edge, no MinIO sync-upload responsibility on EDGE</li>
    <li><strong>Infinite Cluster Scale-Out</strong>: Any number of EDGE nodes join the same EMQX cluster; ordered broker probing with head-of-list retry on failure; supports realtime / snapshot / patrol algorithm task scale-out</li>
    <li><strong>Clear Control-Plane Split</strong>: VIDEO keeps task CRUD, scheduling policy, and log query; EDGE owns the <code>runtime/</code> inference loops and workload launch, and can coexist with the NODE Agent (generic HTTP workloads)</li>
  </ul>
</td>
</tr>
</table>

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
For an in-depth analysis of each module's tech stack, microservice decomposition, middleware topology, and data flows, see <a href=".doc/架构设计/项目架构设计分析_en.md" style="color: #3498db; text-decoration: none; font-weight: 600;">Project Architecture Analysis</a>.
</p>

## 🖥️ Cross-Platform Deployment Advantages

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
EasyAIoT supports deployment on Linux, Mac, and Windows, providing flexible and convenient deployment solutions for users in different environments:
</p>

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; margin: 20px 0;">

<div style="padding: 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🐧 Linux Deployment Advantages</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>Ideal for production environments, stable and reliable with low resource consumption</li>
  <li>Supports Docker containerized deployment with one-click service startup</li>
  <li>Perfect compatibility with servers and edge computing devices (such as RK3588 and other ARM architecture devices)</li>
  <li>Provides complete automated installation scripts to simplify deployment</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🍎 Mac Deployment Advantages</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>Suitable for development and testing environments with deep macOS integration</li>
  <li>Supports local development and debugging for rapid feature validation</li>
  <li>Provides convenient installation scripts compatible with package managers like Homebrew</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🪟 Windows Deployment Advantages</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>Suitable for Windows server environments, reducing learning curve</li>
  <li>Supports PowerShell automation scripts to simplify deployment operations</li>
  <li>Compatible with both Windows Server and desktop Windows systems</li>
  <li>Provides graphical installation wizards for user-friendly experience</li>
</ul>
</div>

</div>


<p style="font-size: 14px; line-height: 1.8; color: #2c3e50; font-weight: 500; margin: 20px 0; padding: 15px; background-color: #e8f4f8; border-left: 4px solid #3498db; border-radius: 4px;">
<strong>Unified Experience</strong>: Regardless of the operating system chosen, EasyAIoT provides consistent installation scripts and deployment documentation, ensuring a uniform cross-platform deployment experience.
</p>

## ☁️ EasyAIoT = AI + IoT = Cloud-Edge Integrated Solution

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
Supports thousands of vertical scenarios with customizable AI models and algorithm development.
</p>

<div style="background-color: #f8f9fa; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #3498db;">
<h3 style="color: #2c3e50; margin-top: 0;">Deep Integration Empowers Intelligent Vision for Everything</h3>
<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 10px 0;">
EasyAIoT constructs an efficient access and management network for IoT devices (especially massive cameras). We deeply integrate real-time streaming technology with cutting-edge AI to create a unified service core. This solution not only enables interoperability across heterogeneous devices but also deeply integrates HD video streams with powerful AI analytics engines, giving surveillance systems "intelligent eyes" – accurately enabling facial recognition, abnormal behavior analysis, risk personnel monitoring, and perimeter intrusion detection.
</p>
<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 10px 0;">
The platform supports two types of algorithm tasks: real-time algorithm tasks for real-time video analysis of RTSP/RTMP streams, providing millisecond-level response capabilities; snapshot algorithm tasks for intelligent analysis of captured images, supporting event backtracking and image retrieval. Through algorithm task management, flexible frame extraction and sorting strategies are achieved, with each task able to bind independent frame extractors and sorters. Combined with model service cluster inference capabilities, millisecond-level response and high availability are ensured. Additionally, two defense strategies are provided: full defense mode and half defense mode, allowing flexible configuration of monitoring rules for different time periods, achieving precise time-based intelligent monitoring and alerting.
</p>
<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 10px 0;">
In terms of IoT device management, EasyAIoT provides comprehensive device lifecycle management capabilities, supporting multiple IoT protocols (MQTT, TCP, HTTP) to achieve rapid device access, secure authentication, real-time monitoring, and intelligent control. Through the rule engine, intelligent data flow and processing of device data are realized, combined with AI capabilities for in-depth analysis of device data, achieving full-process automation from device access, data collection, intelligent analysis to decision execution, truly realizing interconnected everything and intelligent control of everything.
</p>
</div>

<img src=".image/iframe1.jpg" alt="EasyAIoT Platform Architecture" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15);">

## ⚠️ Disclaimer

EasyAIoT is an open-source learning project unrelated to commercial activities. Users must comply with laws and
regulations and refrain from illegal activities. If EasyAIoT discovers user violations, it will cooperate with
authorities and report to government agencies. Users bear full legal responsibility for illegal actions and shall
compensate third parties for damages caused by usage. All EasyAIoT-related resources are used at the user's own risk.

## 📚 Deployment Documentation

- [Platform Deployment Documentation](.doc/部署文档/平台部署文档.md) — Step-by-step deployment guide for Linux / Mac / Windows
- [Deployment Best Practices](.doc/部署文档/部署最佳实践_en.md) — Environment requirements, one-click deployment, troubleshooting, and production recommendations

## 🎮 Demo Environment

- Demo URL: http://36.111.47.113:8888/
- Username: admin
- Password: admin123

## ⚙️ Project Repositories

- Gitee: https://gitee.com/soaring-xiongkulu/easyaiot
- Github: https://github.com/soaring-xiongkulu/easyaiot

## 📸 Screenshots
<div>
  <img src=".image/banner/banner-video1000.gif" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner-video1001.gif" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1091.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1092.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1143.jpg" alt="Platform branding settings" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1144.jpg" alt="Platform branding reset" width="49%">
</div>
<div>
  <img src=".image/banner/banner1001.png" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1076.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1074.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1075.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1095.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1096.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1127.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1128.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1145.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1146.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1147.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1148.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1129.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1130.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1131.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1132.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1133.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1134.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1135.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1136.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1093.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1094.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1085.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1086.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1087.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1088.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1089.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1090.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1078.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1077.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1079.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1080.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1081.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1082.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1006.jpg" alt="Screenshot 3" width="49%">
  <img src=".image/banner/banner1009.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
</div>
<div>
  <img src=".image/banner/banner1051.jpg" alt="Screenshot 8" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1053.jpg" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1062.jpg" alt="Screenshot 8" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1063.png" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1064.jpg" alt="Screenshot 8" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1065.jpg" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1066.jpg" alt="Screenshot 8" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1067.jpg" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1052.jpg" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1054.jpg" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1083.jpg" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1084.jpg" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1121.png" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1122.png" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1123.png" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1124.png" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1125.png" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1126.png" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1057.jpg" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1058.jpg" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1068.jpg" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1069.jpg" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1026.jpg" alt="Screenshot 8" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1028.jpg" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1029.jpg" alt="Screenshot 8" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1030.jpg" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1072.jpg" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1031.jpg" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1070.jpg" alt="Screenshot 8" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1071.jpg" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1033.jpg" alt="Screenshot 8" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1035.jpg" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1034.jpg" alt="Screenshot 8" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1036.jpg" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1037.jpg" alt="Screenshot 8" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1038.jpg" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1015.png" alt="Screenshot 5" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1010.jpg" alt="Screenshot 3" width="49%">
</div>
<div>
  <img src=".image/banner/banner1027.png" alt="Screenshot 2" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1016.jpg" alt="Screenshot 6" width="49%">
</div>
<div>
  <img src=".image/banner/banner1059.jpg" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1060.jpg" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1107.png" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1108.png" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1111.png" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1112.png" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1109.png" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1110.png" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1007.jpg" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1008.jpg" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1103.png" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1104.png" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1105.png" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1106.png" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1019.jpg" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1020.jpg" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1099.png" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1100.png" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1101.png" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1102.png" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1023.jpg" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1024.jpg" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1017.jpg" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1018.jpg" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1097.png" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1098.png" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1039.jpg" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1061.jpg" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1040.jpg" alt="Screenshot 8" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1042.jpg" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1043.jpg" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1044.jpg" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1021.jpg" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1022.jpg" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1045.jpg" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1046.jpg" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1047.jpg" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1048.jpg" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1049.jpg" alt="Screenshot 7" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1050.jpg" alt="Screenshot 8" width="49%">
</div>
<div>
  <img src=".image/banner/banner1013.jpg" alt="Screenshot 9" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1014.png" alt="Screenshot 10" width="49%">
</div>
<div>
  <img src=".image/banner/banner1003.png" alt="Screenshot 13" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1004.png" alt="Screenshot 14" width="49%">
</div>
<div>
  <img src=".image/banner/banner1005.png" alt="Screenshot 15" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1002.png" alt="Screenshot 16" width="49%">
</div>
<div>
  <img src=".image/banner/banner1137.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1138.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1139.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1140.jpg" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1141.jpg" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1142.jpg" alt="Screenshot 1" width="49%">
</div>

## 📞 Contact Information

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
Please follow our official account below first, then reach us via the technical exchange group or WeChat.
</p>

## 👥 Official Account

<div>
  <img src=".image/公众号.jpg" alt="Official Account" width="30%">
</div>

## 💬 Technical Exchange Group

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
After following the official account, scan the QR code below with WeChat to join the EasyAIoT technical exchange group.
</p>

<div>
  <img src=".image/交流群3群.jpg" alt="EasyAIoT Technical Exchange Group" width="30%">
</div>

## 💬 WeChat Contact

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
After following the official account, scan the QR code below to add us as a WeChat friend for one-on-one communication.
</p>

<div>
  <img src=".image/微信联系方式.jpg" alt="WeChat Contact" width="200">
</div>

## 🪐 Knowledge Planet:

<p>
  <img src=".image/知识星球.jpg" alt="知识星球" width="30%">
</p>

## 💰 Sponsorship

<div>
    <img src=".image/微信支付.jpg" alt="WeChat Pay" width="30%" height="30%">
    <img src=".image/支付宝支付.jpg" alt="Alipay" width="30%" height="10%">
</div>

## 🤝 Contributing

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
We welcome all forms of contributions! Whether you are a code developer, documentation writer, or issue reporter, your contribution will help make EasyAIoT better. Here are the main ways to contribute:
</p>

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; margin: 20px 0;">

<div style="padding: 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">💻 Code Contribution</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>Fork the project to your GitHub/Gitee account</li>
  <li>Create a feature branch (git checkout -b feature/AmazingFeature)</li>
  <li>Commit your changes (git commit -m 'Add some AmazingFeature')</li>
  <li>Push to the branch (git push origin feature/AmazingFeature)</li>
  <li>Open a Pull Request</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">📚 Documentation Contribution</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>Improve existing documentation content</li>
  <li>Add usage examples and best practices</li>
  <li>Provide multilingual translations</li>
  <li>Fix documentation errors</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🌟 Other Ways to Contribute</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>Report and fix bugs</li>
  <li>Suggest feature improvements</li>
  <li>Participate in community discussions and help other developers</li>
  <li>Share usage experiences and case studies</li>
</ul>
</div>

</div>

## 🌟 Major Contributors

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
The following are outstanding contributors who have made significant contributions to the EasyAIoT project. Their contributions have played a key role in promoting the project's development. We express our most sincere gratitude!
</p>

<table style="width: 100%; table-layout: fixed; border-collapse: collapse; margin: 20px 0; font-size: 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
<thead>
<tr style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white;">
<th style="padding: 15px; text-align: left; font-weight: 600; border: 1px solid #e0e0e0; width: 32%; min-width: 9rem;">Contributor</th>
<th style="padding: 15px; text-align: left; font-weight: 600; border: 1px solid #e0e0e0;">Contribution</th>
</tr>
</thead>
<tbody>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>℡夏别</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">Contributed Windows deployment documentation for the EasyAIoT project, providing a complete deployment guide for Windows platform users, greatly reducing the deployment difficulty in Windows environments, and enabling more users to easily use the EasyAIoT platform.</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>YiYaYiYaho</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">Contributed Mac container one-click deployment script for the EasyAIoT project, providing an automated deployment solution for Mac platform users, significantly simplifying the deployment process in Mac environments, and improving the deployment experience for developers and users.</td>
</tr>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>山寒</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">Contributed Linux container deployment script for the EasyAIoT project, providing a containerized deployment solution for Linux platform users, achieving fast and reliable container deployment, and providing important guarantees for stable operation in production environments.</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>玖零。</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">Contributed Linux container deployment script for the EasyAIoT project, further improving the containerized deployment solution for Linux platforms, providing more options for users of different Linux distributions, and promoting the project's cross-platform deployment capabilities.</td>
</tr>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>爱吃小柚子</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">To advance EasyAIoT in video surveillance and intelligent analytics, led end-to-end integration and validation testing of the national standard GB28181 with AI business workflows; also carried out dedicated testing and evaluation of image clarity and playback smoothness, providing a strong basis for reliable GB28181 access, link stability, and continuous improvement of the viewing experience.</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>Dark</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">Contributed end-to-end integration of GB28181 for EasyAIoT in national-standard video surveillance, delivering video playback and PTZ (pan-tilt) control so that device access supports practical live preview and remote camera steering.</td>
</tr>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>machh</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">Contributed to the EasyAIoT-Edge project by validating camera onboarding and AI capabilities end to end, and wiring these features into a coherent edge-side workflow.</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>遗忘的星空</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">Contributed to EasyAIoT's direct device onboarding by delivering a multi-vendor IP camera asset inventory and subnet scanner, supporting batch discovery and identification of Hikvision IPCs, NVRs, and related devices; improved batch search and one-click registration for directly connected devices across same-subnet and cross-subnet scenarios. Device access is implemented via native protocols, bypassing the Hikvision SDK and reducing reliance on the Hikvision platform—laying the groundwork for open, controllable large-scale camera onboarding.</td>
</tr>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>阿龙</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">To advance EasyAIoT in map visualization and spatial intelligence, independently contributed the complete implementation of Tianditu spatial visualization capabilities, covering national Tianditu basemap integration, camera and alarm device placement, map distribution views, location search and batch coordinate import, automatic alarm event mapping, person/vehicle trajectory tracking, and mobile device track playback—bringing the platform's "Tianditu spatial visualization and map-based analysis" capability from design to production-ready, usable form.</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>雨落流殇</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">To advance EasyAIoT in ultra-large-scale streaming media delivery, contributed the deployment architecture and scheduling approach for heterogeneous SRS and ZLMediaKit streaming media server clusters, proposing scalable solutions including multi-node pool coordination, decoupling of the streaming media control plane from the business layer, storage and upload pipelines, and node registration scheduling—laying an important architectural foundation for the platform to support concurrent access of tens of thousands of camera streams with stable distribution and elastic scaling.</td>
</tr>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>常康</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">To advance EasyAIoT in intelligent transportation and vehicle management, independently contributed the license plate recognition algorithm and complete code implementation, covering ONNX-based plate detection, plate number and color recognition, double-layer plate merging and tilt/perspective correction, plate library management and multi-library sequential matching, one-click integration with algorithm tasks, and Kafka asynchronous matching—supporting mainstream plate types including blue, yellow, green, white, and new energy vehicle plates—bringing the platform's "license plate recognition and plate library management" capability from planning to production-ready, closed-loop application.</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>Li</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">To advance EasyAIoT in youth developer community building and collaborative ecosystem development, demonstrated outstanding organizational leadership and rallying power by leading fellow students across campus to actively co-build the project, bringing together young talent and collective momentum to inject a continuous, enduring stream of growth energy into EasyAIoT; also made pivotal, irreplaceable contributions in project outreach, hands-on implementation, and cultivating the next generation of contributors.</td>
</tr>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>陈家林</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">To advance EasyAIoT in IoT device interoperability and air–ground video fusion, delivered a closed loop for device commands and status data so the platform can truly “send commands down, see status, and stay in control”; also contributed DJI FlightHub dock and drone video integration, bringing aerial inspection views into the unified video and alarm system, and significantly expanding the platform’s value in wide-area patrol, emergency survey, and sky–ground collaborative sensing.</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>NULL</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">To advance EasyAIoT in industrial field device access, delivered Modbus protocol uplink acquisition so data from meters, sensors, controllers and other industrial equipment can be unified for aggregation, monitoring and linkage—completing the key puzzle of “seeing the scene and hearing the devices,” and providing a solid foundation for industrial data acquisition, production-line intelligence, and security linkage scenarios.</td>
</tr>
</tbody>
</table>

<p style="font-size: 14px; line-height: 1.8; color: #2c3e50; font-weight: 500; margin: 20px 0; padding: 15px; background-color: #e8f4f8; border-left: 4px solid #3498db; border-radius: 4px;">
<strong>Special Thanks</strong>: The work of the above contributors has advanced EasyAIoT in many ways, including cross-platform deployment documentation and scripts, delivery of national-standard video capabilities (including GB28181), AI integration testing, multi-vendor camera direct discovery and batch onboarding, production-ready Tianditu spatial visualization, heterogeneous streaming media cluster deployment and scheduling architecture, production-ready license plate recognition algorithm and complete implementation, EasyAIoT-Edge end-to-end integration linking camera access with AI, campus developer community organization and youth collaborative ecosystem building, IoT device uplink/downlink closed loop and DJI FlightHub aerial view integration, and industrial Modbus device uplink acquisition. Their professionalism and selfless dedication are worthy of our learning and respect. Once again, we express our most sincere gratitude to these outstanding contributors! 🙏
</p>

## 💝 Open Source Guardians

Sustaining an open-source project takes more than code and documentation. During the days when EasyAIoT's compute resources were most strained and the project was on the brink of stalling, the following individuals stepped forward with tangible financial support that gave the project the momentum it needed to keep going. You may never have submitted a single line of code, yet every act of trust and support helped EasyAIoT cross its hardest hurdles and continue to evolve. As long as people use it and stand behind it, the open-source ecosystem deserves to go further; what EasyAIoT has achieved today would not have been possible without these companions who reached out at critical moments. We extend our deepest respect and gratitude to every friend who lent a hand. The following rankings are in no particular order:

<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/默者.png" width="80px;" alt="默者"/><br /><sub><b>默者</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/小满藏舟.png" width="80px;" alt="小满藏舟"/><br /><sub><b>小满藏舟</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/空空.png" width="80px;" alt="空空"/><br /><sub><b>空空</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/阿涛.png" width="80px;" alt="阿涛"/><br /><sub><b>阿涛</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/火车叨位去、.png" width="80px;" alt="火车叨位去、"/><br /><sub><b>火车叨位去、</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/NULL.png" width="80px;" alt="NULL"/><br /><sub><b>NULL</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/一片天.png" width="80px;" alt="一片天"/><br /><sub><b>一片天</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/舍得.png" width="80px;" alt="舍得"/><br /><sub><b>舍得</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/M.png" width="80px;" alt="M"/><br /><sub><b>M</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/南北.png" width="80px;" alt="南北"/><br /><sub><b>南北</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/西乡一粒沙.png" width="80px;" alt="西乡一粒沙"/><br /><sub><b>西乡一粒沙</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/payne.png" width="80px;" alt="payne"/><br /><sub><b>payne</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/滕虎.png" width="80px;" alt="滕虎"/><br /><sub><b>滕虎</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/天天.png" width="80px;" alt="天天"/><br /><sub><b>天天</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/王超.png" width="80px;" alt="王超"/><br /><sub><b>王超</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/最后的轻语.png" width="80px;" alt="最后的轻语"/><br /><sub><b>最后的轻语</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/yang.png" width="80px;" alt="yang"/><br /><sub><b>yang</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/子非鱼.png" width="80px;" alt="子非鱼"/><br /><sub><b>子非鱼</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/在路上.png" width="80px;" alt="在路上"/><br /><sub><b>在路上</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/忘记时间.png" width="80px;" alt="忘记时间"/><br /><sub><b>忘记时间</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/何行者.png" width="80px;" alt="何行者"/><br /><sub><b>何行者</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/ANDY.png" width="80px;" alt="ANDY"/><br /><sub><b>ANDY</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/A许庆.png" width="80px;" alt="A许庆"/><br /><sub><b>A许庆</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/刘兆中📶⁵ᴳ.png" width="80px;" alt="刘兆中📶⁵ᴳ"/><br /><sub><b>刘兆中📶⁵ᴳ</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/冯.png" width="80px;" alt="冯"/><br /><sub><b>冯</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/莫斯克.png" width="80px;" alt="莫斯克"/><br /><sub><b>莫斯克</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/赵欢.png" width="80px;" alt="赵欢"/><br /><sub><b>赵欢</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/前进!.png" width="80px;" alt="前进!"/><br /><sub><b>前进!</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/永恒.png" width="80px;" alt="永恒"/><br /><sub><b>永恒</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/Catwings.png" width="80px;" alt="Catwings"/><br /><sub><b>Catwings</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/刘振达.png" width="80px;" alt="刘振达"/><br /><sub><b>刘振达</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/雷沛奇.png" width="80px;" alt="雷沛奇"/><br /><sub><b>雷沛奇</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/CSL.png" width="80px;" alt="CSL"/><br /><sub><b>CSL</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/自胜.png" width="80px;" alt="自胜"/><br /><sub><b>自胜</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/朱江山.png" width="80px;" alt="朱江山"/><br /><sub><b>朱江山</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/安.png" width="80px;" alt="安"/><br /><sub><b>安</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/简单.png" width="80px;" alt="简单"/><br /><sub><b>简单</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/郝艳军.png" width="80px;" alt="郝艳军"/><br /><sub><b>郝艳军</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/Star&Li.png" width="80px;" alt="Star&Li"/><br /><sub><b>Star&Li</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/工体东路.png" width="80px;" alt="工体东路"/><br /><sub><b>工体东路</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/Sunder..png" width="80px;" alt="Sunder."/><br /><sub><b>Sunder.</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/程亮🌟.png" width="80px;" alt="程亮🌟"/><br /><sub><b>程亮🌟</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/should.png" width="80px;" alt="should"/><br /><sub><b>should</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/黄国洪.png" width="80px;" alt="黄国洪"/><br /><sub><b>黄国洪</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/Holmesian.png" width="80px;" alt="Holmesian"/><br /><sub><b>Holmesian</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/Issac.png" width="80px;" alt="Issac"/><br /><sub><b>Issac</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/习惯.png" width="80px;" alt="习惯"/><br /><sub><b>习惯</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/黄杰.png" width="80px;" alt="黄杰"/><br /><sub><b>黄杰</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/唐智灵.png" width="80px;" alt="唐智灵"/><br /><sub><b>唐智灵</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/巴波儿奔🇨🇳.png" width="80px;" alt="巴波儿奔🇨🇳"/><br /><sub><b>巴波儿奔🇨🇳</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/冯振华.png" width="80px;" alt="冯振华"/><br /><sub><b>冯振华</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/风清扬.png" width="80px;" alt="风清扬"/><br /><sub><b>风清扬</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/take your time or.png" width="80px;" alt="take your time or"/><br /><sub><b>take your time or</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/Rising徐.png" width="80px;" alt="Rising徐"/><br /><sub><b>Rising徐</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/Mr.G.png" width="80px;" alt="Mr.G"/><br /><sub><b>Mr.G</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/吴翕然.png" width="80px;" alt="吴翕然"/><br /><sub><b>吴翕然</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/蓝天白云.png" width="80px;" alt="蓝天白云"/><br /><sub><b>蓝天白云</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/Charlie.png" width="80px;" alt="Charlie"/><br /><sub><b>Charlie</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/胖哥.png" width="80px;" alt="胖哥"/><br /><sub><b>胖哥</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/王宪芳.png" width="80px;" alt="王宪芳"/><br /><sub><b>王宪芳</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/lk.png" width="80px;" alt="lk"/><br /><sub><b>lk</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src=".image/open-source-guardian/阿旺.png" width="80px;" alt="阿旺*"/><br /><sub><b>阿旺*</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/🍃一笑奈何🍃.png" width="80px;" alt="🍃一笑奈何🍃"/><br /><sub><b>🍃一笑奈何🍃</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/刘召.png" width="80px;" alt="刘召"/><br /><sub><b>刘召</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/🍻Jamie.png" width="80px;" alt="🍻Jamie"/><br /><sub><b>🍻Jamie</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/薛磊.png" width="80px;" alt="薛磊"/><br /><sub><b>薛磊</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/Jack.png" width="80px;" alt="Jack"/><br /><sub><b>Jack</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/啊这.png" width="80px;" alt="啊这"/><br /><sub><b>啊这</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/在希望德田野上.png" width="80px;" alt="在希望德田野上"/><br /><sub><b>在希望德田野上</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/莫建民.png" width="80px;" alt="莫建民"/><br /><sub><b>莫建民</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/马景祥.png" width="80px;" alt="马景祥"/><br /><sub><b>马景祥</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/谭远彪.png" width="80px;" alt="谭远彪"/><br /><sub><b>谭远彪</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/一杯陈豆浆🥲🥲.png" width="80px;" alt="一杯陈豆浆🥲🥲"/><br /><sub><b>一杯陈豆浆🥲🥲</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/chen.png" width="80px;" alt="chen"/><br /><sub><b>chen</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/xingzhedu2030.png" width="80px;" alt="xingzhedu2030"/><br /><sub><b>xingzhedu2030</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/machh.png" width="80px;" alt="machh"/><br /><sub><b>machh</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/开炫🍊🍊🍊.png" width="80px;" alt="开炫🍊🍊🍊"/><br /><sub><b>开炫🍊🍊🍊</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/Dark.png" width="80px;" alt="Dark"/><br /><sub><b>Dark</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/A-Tree.png" width="80px;" alt="A-Tree"/><br /><sub><b>A-Tree</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/陈.png" width="80px;" alt="陈"/><br /><sub><b>陈</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/月半.png" width="80px;" alt="月半"/><br /><sub><b>月半</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/吴军.png" width="80px;" alt="吴军"/><br /><sub><b>吴军</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/青衫.png" width="80px;" alt="青衫"/><br /><sub><b>青衫</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/梓淇東來.png" width="80px;" alt="梓淇東來"/><br /><sub><b>梓淇東來</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/潇潇.png" width="80px;" alt="潇潇"/><br /><sub><b>潇潇</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/依依.png" width="80px;" alt="依依"/><br /><sub><b>依依</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/金·郁金香.png" width="80px;" alt="金·郁金香"/><br /><sub><b>金·郁金香</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/David.png" width="80px;" alt="David"/><br /><sub><b>David</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/榕德天锐-邱国城.png" width="80px;" alt="榕德天锐-邱国城"/><br /><sub><b>榕德天锐-邱国城</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/Wzs.png" width="80px;" alt="Wzs"/><br /><sub><b>Wzs</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/张军伟.png" width="80px;" alt="张军伟"/><br /><sub><b>张军伟</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/菜rainbow狗.png" width="80px;" alt="菜rainbow狗"/><br /><sub><b>菜rainbow狗</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/闻达.png" width="80px;" alt="闻达"/><br /><sub><b>闻达</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/银之匙.png" width="80px;" alt="银之匙"/><br /><sub><b>银之匙</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/命中注定.png" width="80px;" alt="命中注定"/><br /><sub><b>命中注定</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/..png" width="80px;" alt="..."/><br /><sub><b>...</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/爱吃小柚子.png" width="80px;" alt="爱吃小柚子"/><br /><sub><b>爱吃小柚子</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/草原雄鹰.png" width="80px;" alt="草原雄鹰"/><br /><sub><b>草原雄鹰</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/顺流致远.png" width="80px;" alt="顺流致远"/><br /><sub><b>顺流致远</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/香草口味.png" width="80px;" alt="香草口味"/><br /><sub><b>香草口味</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/雨落流殇.png" width="80px;" alt="雨落流殇"/><br /><sub><b>雨落流殇</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/弱电安防.png" width="80px;" alt="弱电安防"/><br /><sub><b>弱电安防</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/山里人.png" width="80px;" alt="山里人"/><br /><sub><b>山里人</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/诗如画.png" width="80px;" alt="诗如画"/><br /><sub><b>诗如画</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/星空🌃.png" width="80px;" alt="星空🌃"/><br /><sub><b>星空🌃</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/楠哥.png" width="80px;" alt="楠哥"/><br /><sub><b>楠哥</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/蜗牛.png" width="80px;" alt="蜗牛"/><br /><sub><b>蜗牛</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/大周.png" width="80px;" alt="大周"/><br /><sub><b>大周</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/歌德de花烛.png" width="80px;" alt="歌德de花烛"/><br /><sub><b>歌德de花烛</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/noname.png" width="80px;" alt="noname"/><br /><sub><b>noname</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/兔子.png" width="80px;" alt="兔子"/><br /><sub><b>兔子</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/ThinkInStack.png" width="80px;" alt="ThinkInStack"/><br /><sub><b>ThinkInStack</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/Louis.png" width="80px;" alt="Louis"/><br /><sub><b>Louis</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/胡首凡 梯控门禁五方对讲.png" width="80px;" alt="胡首凡 梯控门禁五方对讲"/><br /><sub><b>胡首凡 梯控门禁五方对讲</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/袁建华.png" width="80px;" alt="袁建华"/><br /><sub><b>袁建华</b></sub></a></td>
    </tr>
  </tbody>
</table>

## 🏆 Best Practitioners

They are the pioneers who push EasyAIoT from "usable" to "easy to use and use well" — the following individuals have completed EasyAIoT project deployment or business scenario implementation. Their exploration and achievements set replicable and referable benchmarks for the community. We extend our highest respect and heartfelt congratulations to these outstanding practitioners! The following rankings are in no particular order:

<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/best-practitioner/℡夏别.jpg" width="80px;" alt="℡夏别"/><br /><sub><b>℡夏别</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/best-practitioner/YiYaYiYaho.jpg" width="80px;" alt="YiYaYiYaho"/><br /><sub><b>YiYaYiYaho</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/best-practitioner/冯.jpg" width="80px;" alt="冯"/><br /><sub><b>冯</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/best-practitioner/在希望德田野上.jpg" width="80px;" alt="在希望德田野上"/><br /><sub><b>在希望德田野上</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/best-practitioner/漠然.png" width="80px;" alt="漠然"/><br /><sub><b>漠然</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/best-practitioner/爱吃小柚子.jpg" width="80px;" alt="爱吃小柚子"/><br /><sub><b>爱吃小柚子</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/best-practitioner/Wzs.jpg" width="80px;" alt="Wzs"/><br /><sub><b>Wzs</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/best-practitioner/Dark.jpg" width="80px;" alt="Dark"/><br /><sub><b>Dark</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/best-practitioner/刘延波.jpg" width="80px;" alt="刘延波"/><br /><sub><b>刘延波</b></sub></a></td>
    </tr>
  </tbody>
</table>

## 🙏 Acknowledgements

Thanks to the following contributors for code, feedback, donations, and support (in no particular order):
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/默者.png" width="80px;" alt="默者"/><br /><sub><b>默者</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/小满藏舟.png" width="80px;" alt="小满藏舟"/><br /><sub><b>小满藏舟</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/空空.png" width="80px;" alt="空空"/><br /><sub><b>空空</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/chen_jialin123" target="_blank"><img src="./.image/sponsor/陈家林.png" width="80px;" alt="陈家林"/><br /><sub><b>陈家林</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/NULL.png" width="80px;" alt="NULL"/><br /><sub><b>NULL</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/陈勇至.jpg" width="80px;" alt="陈勇至"/><br /><sub><b>陈勇至</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/Dark.jpg" width="80px;" alt="Dark"/><br /><sub><b>Dark</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://github.com/machh" target="_blank"><img src="./.image/sponsor/machh.jpg" width="80px;" alt="machh"/><br /><sub><b>machh</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/三块两毛四.jpg" width="80px;" alt="三块两毛四"/><br /><sub><b>三块两毛四</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/物语晨水²⁰²⁶.jpg" width="80px;" alt="物语晨水²⁰²⁶"/><br /><sub><b>物语晨水²⁰²⁶</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/L_Z_M" target="_blank"><img src=".image/sponsor/玖零。.jpg" width="80px;" alt="玖零。"/><br /><sub><b>玖零。</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/36436022" target="_blank"><img src=".image/sponsor/金鸿伟.jpg" width="80px;" alt="金鸿伟"/><br /><sub><b>金鸿伟</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/cnlijf" target="_blank"><img src="./.image/sponsor/李江峰.jpg" width="80px;" alt="李江峰"/><br /><sub><b>李江峰</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src=".image/sponsor/Best%20Yao.jpg" width="80px;" alt="Best Yao"/><br /><sub><b>Best Yao</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/weiloser" target="_blank"><img src=".image/sponsor/无为而治.jpg" width="80px;" alt="无为而治"/><br /><sub><b>无为而治</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/shup092_admin" target="_blank"><img src="./.image/sponsor/shup.jpg" width="80px;" alt="shup"/><br /><sub><b>shup</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/gampa" target="_blank"><img src="./.image/sponsor/也许.jpg" width="80px;" alt="也许"/><br /><sub><b>也许</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/leishaozhuanshudi" target="_blank"><img src="./.image/sponsor/⁰ʚᦔrꫀꪖꪑ⁰ɞ%20..jpg" width="80px;" alt="⁰ʚᦔrꫀꪖꪑ⁰ɞ ."/><br /><sub><b>⁰ʚᦔrꫀꪖꪑ⁰ɞ .</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/fateson" target="_blank"><img src="./.image/sponsor/逆.jpg" width="80px;" alt="逆"/><br /><sub><b>逆</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/dongGezzz_admin" target="_blank"><img src="./.image/sponsor/廖东旺.jpg" width="80px;" alt="廖东旺"/><br /><sub><b>廖东旺</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/huangzhen1993" target="_blank"><img src="./.image/sponsor/黄振.jpg" width="80px;" alt="黄振"/><br /><sub><b>黄振</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://github.com/fengchunshen" target="_blank"><img src="./.image/sponsor/春生.jpg" width="80px;" alt="春生"/><br /><sub><b>春生</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/mrfox_wang" target="_blank"><img src="./.image/sponsor/贵阳王老板.jpg" width="80px;" alt="贵阳王老板"/><br /><sub><b>贵阳王老板</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/haobaby" target="_blank"><img src="./.image/sponsor/hao_chen.jpg" width="80px;" alt="hao_chen"/><br /><sub><b>hao_chen</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/finalice" target="_blank"><img src="./.image/sponsor/尽千.jpg" width="80px;" alt="尽千"/><br /><sub><b>尽千</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/yuer629" target="_blank"><img src="./.image/sponsor/yuer629.jpg" width="80px;" alt="yuer629"/><br /><sub><b>yuer629</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/cai-peikai/ai-project" target="_blank"><img src="./.image/sponsor/kong.jpg" width="80px;" alt="kong"/><br /><sub><b>kong</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/HB1731276584" target="_blank"><img src="./.image/sponsor/岁月静好.jpg" width="80px;" alt="岁月静好"/><br /><sub><b>岁月静好</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/hy5128" target="_blank"><img src="./.image/sponsor/Kunkka.jpg" width="80px;" alt="Kunkka"/><br /><sub><b>Kunkka</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/guo-dida" target="_blank"><img src="./.image/sponsor/灬.jpg" width="80px;" alt="灬"/><br /><sub><b>灬</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://github.com/XyhBill" target="_blank"><img src="./.image/sponsor/Mr.LuCkY.jpg" width="80px;" alt="Mr.LuCkY"/><br /><sub><b>Mr.LuCkY</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/timeforeverz" target="_blank"><img src="./.image/sponsor/泓.jpg" width="80px;" alt="泓"/><br /><sub><b>泓</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/mySia" target="_blank"><img src="./.image/sponsor/i.jpg" width="80px;" alt="i"/><br /><sub><b>i</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/依依.jpg" width="80px;" alt="依依"/><br /><sub><b>依依</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/sunbirder" target="_blank"><img src="./.image/sponsor/小菜鸟先飞.jpg" width="80px;" alt="小菜鸟先飞"/><br /><sub><b>小菜鸟先飞</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/mmy0" target="_blank"><img src="./.image/sponsor/追溯未来-_-.jpg" width="80px;" alt="追溯未来"/><br /><sub><b>追溯未来</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/ccqingshan" target="_blank"><img src="./.image/sponsor/青衫.jpg" width="80px;" alt="青衫"/><br /><sub><b>青衫</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/jiangchunJava" target="_blank"><img src="./.image/sponsor/Fae.jpg" width="80px;" alt="Fae"/><br /><sub><b>Fae</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/huang-xiangtai" target="_blank"><img src="./.image/sponsor/憨憨.jpg" width="80px;" alt="憨憨"/><br /><sub><b>憨憨</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/gu-beichen-starlight" target="_blank"><img src="./.image/sponsor/文艺小青年.jpg" width="80px;" alt="文艺小青年"/><br /><sub><b>文艺小青年</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://github.com/zhangnanchao" target="_blank"><img src="./.image/sponsor/lion.jpg" width="80px;" alt="lion"/><br /><sub><b>lion</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/yupccc" target="_blank"><img src="./.image/sponsor/汪汪队立大功.jpg" width="80px;" alt="汪汪队立大功"/><br /><sub><b>汪汪队立大功</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/wcjjjjjjj" target="_blank"><img src="./.image/sponsor/wcj.jpg" width="80px;" alt="wcj"/><br /><sub><b>wcj</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/hufanglei" target="_blank"><img src="./.image/sponsor/🌹怒放de生命😋.jpg" width="80px;" alt="怒放de生命"/><br /><sub><b>怒放de生命</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/juyunsuan" target="_blank"><img src="./.image/sponsor/蓝速传媒.jpg" width="80px;" alt="蓝速传媒"/><br /><sub><b>蓝速传媒</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/achieve275" target="_blank"><img src="./.image/sponsor/Achieve_Xu.jpg" width="80px;" alt="Achieve_Xu"/><br /><sub><b>Achieve_Xu</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/nicholasld" target="_blank"><img src="./.image/sponsor/NicholasLD.jpg" width="80px;" alt="NicholasLD"/><br /><sub><b>NicholasLD</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/ADVISORYZ" target="_blank"><img src=".image/sponsor/ADVISORYZ.jpg" width="80px;" alt="ADVISORYZ"/><br /><sub><b>ADVISORYZ</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/dongxinji" target="_blank"><img src="./.image/sponsor/take%20your%20time%20or.jpg" width="80px;" alt="take your time or"/><br /><sub><b>take your time or</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://github.com/xu756" target="_blank"><img src="./.image/sponsor/碎碎念..jpg" width="80px;" alt="碎碎念."/><br /><sub><b>碎碎念.</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/lwisme" target="_blank"><img src="./.image/sponsor/北街.jpg" width="80px;" alt="北街"/><br /><sub><b>北街</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/yu-xinyan71" target="_blank"><img src="./.image/sponsor/Dorky%20TAT.jpg" width="80px;" alt="Dorky TAT"/><br /><sub><b>Dorky TAT</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/chenxiaohong" target="_blank"><img src=".image/sponsor/右耳向西.jpg" width="80px;" alt="右耳向西"/><br /><sub><b>右耳向西</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://github.com/派大星" target="_blank"><img src="./.image/sponsor/派大星.jpg" width="80px;" alt="派大星"/><br /><sub><b>派大星</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/wz_vue_gitee_181" target="_blank"><img src="./.image/sponsor/棒槌🧿🍹🍹🧿.jpg" width="80px;" alt="棒槌🧿🍹🍹🧿"/><br /><sub><b>棒槌</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/nctwo" target="_blank"><img src=".image/sponsor/信微输传助手.jpg" width="80px;" alt="信微输传助手"/><br /><sub><b>信微输传助手</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/l9999_admin" target="_blank"><img src=".image/sponsor/一往无前.jpg" width="80px;" alt="一往无前"/><br /><sub><benen>一往无前</benen></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/stenin" target="_blank"><img src="./.image/sponsor/Charon.jpg" width="80px;" alt="Charon"/><br /><sub><b>Charon</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/zhao-yihuiwifi" target="_blank"><img src="./.image/sponsor/赵WIFI..jpg" width="80px;" alt="赵WIFI."/><br /><sub><b>赵WIFI.</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/Yang619" target="_blank"><img src="./.image/sponsor/Chao..jpg" width="80px;" alt="Chao."/><br /><sub><b>Chao.</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/lcrsd123" target="_blank"><img src=".image/sponsor/城市稻草人.jpg" width="80px;" alt="城市稻草人"/><br /><sub><b>城市稻草人</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/Mo_bai1016" target="_blank"><img src=".image/sponsor/Bug写手墨白.jpg" width="80px;" alt="Bug写手墨白"/><br /><sub><b>Bug写手墨白</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/kevinosc_admin" target="_blank"><img src=".image/sponsor/kevin.jpg" width="80px;" alt="kevin"/><br /><sub><b>kevin</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/lhyicn" target="_blank"><img src=".image/sponsor/童年.jpg" width="80px;" alt="童年"/><br /><sub><b>童年</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/dubai100" target="_blank"><img src="./.image/sponsor/sherry金.jpg" width="80px;" alt="sherry金"/><br /><sub><b>sherry金</b></sub></a></td>
    </tr>
    <tr>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/℡夏别.jpg" width="80px;" alt="℡夏别"/><br /><sub><b>℡夏别</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/翠翠草原.jpg" width="80px;" alt="翠翠草原"/><br /><sub><b>翠翠草原</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/慕容曦.jpg" width="80px;" alt="慕容曦"/><br /><sub><b>慕容曦</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/Tyrion.jpg" width="80px;" alt="Tyrion"/><br /><sub><b>Tyrion</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/大漠孤烟.jpg" width="80px;" alt="大漠孤烟"/><br /><sub><b>大漠孤烟</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/Return.jpg" width="80px;" alt="Return"/><br /><sub><b>Return</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/一杯拿铁.jpg" width="80px;" alt="一杯拿铁"/><br /><sub><b>一杯拿铁</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/Thuri.jpg" width="80px;" alt="Thuri"/><br /><sub><b>Thuri</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/Liu.jpg" width="80px;" alt="Liu"/><br /><sub><b>Liu</b></sub></a></td>
    </tr>
    <tr>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/三金.jpg" width="80px;" alt="三金"/><br /><sub><b>三金</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/ZPort.jpg" width="80px;" alt="ZPort"/><br /><sub><b>ZPort</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/Li.jpg" width="80px;" alt="Li"/><br /><sub><b>Li</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/嘉树.jpg" width="80px;" alt="嘉树"/><br /><sub><b>嘉树</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/俊采星驰.jpg" width="80px;" alt="俊采星驰"/><br /><sub><b>俊采星驰</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/oi.jpg" width="80px;" alt="oi"/><br /><sub><b>oi</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/ZhangY_000.jpg" width="80px;" alt="ZhangY_000"/><br /><sub><b>ZhangY_000</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/℡夏别.jpg" width="80px;" alt="℡夏别"/><br /><sub><b>℡夏别</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/张瑞麟.jpg" width="80px;" alt="张瑞麟"/><br /><sub><b>张瑞麟</b></sub></a></td>
    </tr>
    <tr>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/Lion King.jpg" width="80px;" alt="Lion King"/><br /><sub><b>Lion King</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/Frank.jpg" width="80px;" alt="Frank"/><br /><sub><b>Frank</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/徐梦阳.jpg" width="80px;" alt="徐梦阳"/><br /><sub><b>徐梦阳</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/九月.jpg" width="80px;" alt="九月"/><br /><sub><b>九月</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/tangl伟.jpg" width="80px;" alt="tangl伟"/><br /><sub><b>tangl伟</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/冯瑞伦.jpg" width="80px;" alt="冯瑞伦"/><br /><sub><b>冯瑞伦</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/杨林.jpg" width="80px;" alt="杨林"/><br /><sub><b>杨林</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/梧桐有语。.jpg" width="80px;" alt="梧桐有语。"/><br /><sub><b>梧桐有语。</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/歌德de花烛.jpg" width="80px;" alt="歌德de花烛"/><br /><sub><b>歌德de花烛</b></sub></a></td>
    </tr>
    <tr>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/泥嚎.jpg" width="80px;" alt="泥嚎"/><br /><sub><b>泥嚎</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/翠翠草原.jpg" width="80px;" alt="翠翠草原"/><br /><sub><b>翠翠草原</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/胡泽龙.jpg" width="80px;" alt="胡泽龙"/><br /><sub><b>胡泽龙</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/苏叶.jpg" width="80px;" alt="苏叶"/><br /><sub><b>苏叶</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/裴先生.jpg" width="80px;" alt="裴先生"/><br /><sub><b>裴先生</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/谭远彪.jpg" width="80px;" alt="谭远彪"/><br /><sub><b>谭远彪</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/陈祺.jpg" width="80px;" alt="陈祺"/><br /><sub><b>陈祺</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/零点就睡.jpg" width="80px;" alt="零点就睡"/><br /><sub><b>零点就睡</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/风之羽.jpg" width="80px;" alt="风之羽"/><br /><sub><b>风之羽</b></sub></a></td>
    </tr>
    <tr>
        <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/fufeng1908" target="_blank"><img src="./.image/sponsor/王守仁.jpg" width="80px;" alt="王守仁"/><br /><sub><b>王守仁</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/kaigejava" target="_blank"><img src="./.image/sponsor/狼ྂ图ྂ腾ྂ.jpg" width="80px;" alt="狼图腾"/><br /><sub><b>狼图腾</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/马到成功.jpg" width="80px;" alt="马到成功"/><br /><sub><b>马到成功</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/做生活的高手.jpg" width="80px;" alt="做生活的高手"/><br /><sub><b>做生活的高手</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/清欢之恋.jpg" width="80px;" alt="清欢之恋"/><br /><sub><b>清欢之恋</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/绝域时空.jpg" width="80px;" alt="绝域时空"/><br /><sub><b>绝域时空</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/风雨.jpg" width="80px;" alt="风雨"/><br /><sub><b>风雨</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/Nicola.jpg" width="80px;" alt="Nicola"/><br /><sub><b>Nicola</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/云住.jpg" width="80px;" alt="云住"/><br /><sub><b>云住</b></sub></a></td>
    </tr>
    <tr>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/Mr.Zhang.jpg" width="80px;" alt="Mr.Zhang"/><br /><sub><b>Mr.Zhang</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/剑.jpg" width="80px;" alt="剑"/><br /><sub><b>剑</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/shen.jpg" width="80px;" alt="shen"/><br /><sub><b>shen</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/嗯.jpg" width="80px;" alt="嗯"/><br /><sub><b>嗯</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/周华.jpg" width="80px;" alt="周华"/><br /><sub><b>周华</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/太阳鸟.jpg" width="80px;" alt="太阳鸟"/><br /><sub><b>太阳鸟</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/了了.jpg" width="80px;" alt="了了"/><br /><sub><b>了了</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/第七次日落.jpg" width="80px;" alt="第七次日落"/><br /><sub><b>第七次日落</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/npc.jpg" width="80px;" alt="npc"/><br /><sub><b>npc</b></sub></a></td>
    </tr>
    <tr>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/承担不一样的天空.jpg" width="80px;" alt="承担不一样的天空"/><br /><sub><b>承担不一样的天空</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/铁木.jpg" width="80px;" alt="铁木"/><br /><sub><b>铁木</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/Orion.jpg" width="80px;" alt="Orion"/><br /><sub><b>Orion</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/森源-金福洪.jpg" width="80px;" alt="森源-金福洪"/><br /><sub><b>森源-金福洪</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/薛继超.jpg" width="80px;" alt="薛继超"/><br /><sub><b>薛继超</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/虎虎虎.jpg" width="80px;" alt="虎虎虎"/><br /><sub><b>虎虎虎</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/Everyman.jpg" width="80px;" alt="Everyman"/><br /><sub><b>Everyman</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/NXL.jpg" width="80px;" alt="NXL"/><br /><sub><b>NXL</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/孙涛.jpg" width="80px;" alt="孙涛"/><br /><sub><b>孙涛</b></sub></a></td>
    </tr>
    <tr>
        <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/bcake" target="_blank"><img src=".image/sponsor/大饼.jpg" width="80px;" alt="大饼"/><br /><sub><b>大饼</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/hrsjw1.jpg" width="80px;" alt="hrsjw1"/><br /><sub><b>hrsjw1</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/linguanghuan.jpg" width="80px;" alt="linguanghuan"/><br /><sub><b>linguanghuan</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/YiYaYiYaho.jpg" width="80px;" alt="YiYaYiYaho"/><br /><sub><b>YiYaYiYaho</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/慢慢慢.jpg" width="80px;" alt="慢慢慢"/><br /><sub><b>慢慢慢</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/lilOne.jpg" width="80px;" alt="lilOne"/><br /><sub><b>lilOne</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src=".image/sponsor/icon.jpg" width="80px;" alt="icon"/><br /><sub><b>icon</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/jiang4yu" target="_blank"><img src=".image/sponsor/山寒.jpg" width="80px;" alt="山寒"/><br /><sub><b>山寒</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/baobaomo" target="_blank"><img src="./.image/sponsor/放学丶别走.jpg" width="80px;" alt="放学丶别走"/><br /><sub><b>放学丶别走</b></sub></a></td>
    </tr>
    <tr>
        <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/wagger" target="_blank"><img src="./.image/sponsor/春和.jpg" width="80px;" alt="春和"/><br /><sub><b>春和</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/longbinwu" target="_blank"><img src="./.image/sponsor/章鱼小丸子.jpg" width="80px;" alt="章鱼小丸子"/><br /><sub><b>章鱼小丸子</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/Catwings.jpg" width="80px;" alt="Catwings"/><br /><sub><b>Catwings</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/小工头.jpg" width="80px;" alt="小工头"/><br /><sub><b>小工头</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/西乡一粒沙.jpg" width="80px;" alt="西乡一粒沙"/><br /><sub><b>西乡一粒沙</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/爱吃小柚子.jpg" width="80px;" alt="爱吃小柚子"/><br /><sub><b>爱吃小柚子</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/阿龙.jpg" width="80px;" alt="阿龙"/><br /><sub><b>阿龙</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/雨落流殇.jpg" width="80px;" alt="雨落流殇"/><br /><sub><b>雨落流殇</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/遗忘的星空.jpg" width="80px;" alt="遗忘的星空"/><br /><sub><b>遗忘的星空</b></sub></a></td>
    </tr>
    <tr>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/常康.jpg" width="80px;" alt="常康"/><br /><sub><b>常康</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/嘎嗝.jpg" width="80px;" alt="嘎嗝"/><br /><sub><b>嘎嗝</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/曹.jpg" width="80px;" alt="曹"/><br /><sub><b>曹</b></sub></a></td>
        <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/滔滔.jpg" width="80px;" alt="滔滔"/><br /><sub><b>滔滔</b></sub></a></td>
    </tr>
  </tbody>
</table>

## 💡 Expectations

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
We welcome suggestions for improvement to help refine easyaiot.
</p>

## 📄 Copyright

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
Soaring Xiongkulu / easyaiot is licensed under the <a href="https://gitee.com/soaring-xiongkulu/easyaiot/blob/main/LICENSE" style="color: #3498db; text-decoration: none; font-weight: 600;">MIT LICENSE</a>. We are committed to promoting the popularization and development of AI technology, enabling more people to freely use and benefit from this technology.
</p>

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
<strong>Usage License</strong>: Individuals and enterprises can use it 100% free of charge, without the need to retain author or Copyright information. We believe the value of technology lies in its widespread use and continuous innovation, not in being bound by copyright. We hope you can freely use, modify, and distribute this project, making AI technology truly benefit everyone.
</p>

## 🌟 Star Growth Trend Chart

[![Stargazers over time](https://starchart.cc/soaring-xiongkulu/easyaiot.svg?variant=adaptive)](https://starchart.cc/soaring-xiongkulu/easyaiot)