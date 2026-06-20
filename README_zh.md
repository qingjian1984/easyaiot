# EasyAIoT（云边端一体化智能算法应用平台）

[![Gitee star](https://gitee.com/volara/easyaiot/badge/star.svg?theme=gvp)](https://gitee.com/soaring-xiongkulu/easyaiot/stargazers)
[![Gitee fork](https://gitee.com/volara/easyaiot/badge/fork.svg?theme=gvp)](https://gitee.com/soaring-xiongkulu/easyaiot/members)

<p style="font-size: 16px; line-height: 1.8; color: #555; font-weight: 400; margin: 20px 0;">
我希望全世界都能使用这个系统，实现AI的真正0门槛，人人都能体验到AI带来的好处，而并不只是掌握在少数人手里。
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

## 🌟 关于项目的一些思考

### 📍 项目定位

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
EasyAIoT是一个云边端一体化的智能物联网平台，专注于AI与IoT的深度融合。平台通过算法任务管理、实时流分析、模型服务集群推理等核心能力，实现从设备接入到数据采集、AI分析、智能决策的全链路闭环，真正实现万物互联、万物智控。
</p>

#### 🔄 边缘盒子、AI 一体摄像头、AIoT 智能全栈一体机——同一套平台不必拆版本

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
很多智能物联网项目走到落地时都会卡住：<strong>功能做全了，小机器装不下；为了装得下，又得砍能力、拆版本、维护多套部署包。</strong> EasyAIoT 用同一套平台化解这一矛盾——<strong>边缘盒子点上智能、AI 一体摄像头上墙即分析、AIoT 智能全栈一体机一箱配齐全链路</strong>，三类最常见的现场硬件各选一档即可，同一套软件贯穿从单点试点到楼面覆盖再到全栈交付，不必拆版本。
</p>

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 10px 0;">
<strong>你能得到什么：</strong>
</p>

<ul style="font-size: 14px; line-height: 1.8; color: #444; margin: 8px 0 12px 0;">
  <li><strong>盒子也能上智能</strong>：4 GB 边缘盒子不必再当「只能转发视频」的哑终端，摆上就能接入、分析、告警</li>
  <li><strong>摄像头自带大脑</strong>：AI 一体摄像头上墙即可分析，多路摄像头组网即可覆盖楼面、园区，不必每个点位都拖一套盒子加摄像头</li>
  <li><strong>全栈一台配齐</strong>：AIoT 智能全栈一体机把设备接入、视频监控、AI 分析、业务管控装进一台机器，不必拼凑多套系统、多个厂商</li>
  <li><strong>换硬件不换平台</strong>：从盒子试点到摄像头上墙到全栈一体机投产，升级只换硬件档位，业务与数据可延续</li>
</ul>

| 选型 | 典型硬件（举例） | 推荐内存 | 你能做到什么 | 实测验证* |
| :-- | :-- | :--: | :-- | :--: |
| **mini** 边缘精简版 | <strong>边缘盒子</strong>（4 GB 工控机、门店安防一体机、工地现场网关） | ≥ 4 GB | <strong>一个点位装上就有智能</strong>：摄像头接入、实时分析、智能告警、模型推理，最低成本落地视觉能力 | 仅需约 2 GB，余量充足 |
| **standard** 标准版 | <strong>AI 一体摄像头</strong>（智能摄像终端、带算力 AI 监控摄像头、多目 AI 分析一体机） | ≥ 16 GB | <strong>每路摄像头即智能节点</strong>：多路摄像头上墙即可楼面/园区级覆盖，设备、规则、算力统一编排，多场景并行运营 | 约 10 GB，运行平稳有余量 |
| **full** 完整版（默认） | <strong>AIoT 智能全栈一体机</strong>（企业级全栈智控一体机、行业物联网全栈主机、云边端一体智能平台一体机） | ≥ 20 GB | <strong>一箱配齐 IoT + 视频 + AI</strong>：设备纳管、海量接入、智能分析、指挥研判一体化，全量能力长期稳跑 | 约 14 GB，全能力开启仍留足余量 |

<p style="font-size: 13px; line-height: 1.6; color: #888; margin: 8px 0;">
*实测为安装收尾自动核算的参考值，随业务负载浮动；图中绿色「符合」表示当前硬件档位下资源安全、可放心投产。
</p>

<p style="font-size: 14px; line-height: 1.8; color: #444; margin: 12px 0;">
<strong>三类硬件，分别解决什么问题：</strong>
</p>

<ul style="font-size: 14px; line-height: 1.8; color: #444; margin: 8px 0 12px 0;">
  <li><strong>边缘盒子（mini）</strong>：岗亭桌下、门店弱电箱、工地配电间——只有一个点位、预算紧、没人天天运维，但需要今天装上今天就能「看得见异常、判得清事件、告警能推送」</li>
  <li><strong>AI 一体摄像头（standard）</strong>：楼道、出入口、停车场、园区道路——点位多、要铺开，摄像头上墙就能分析告警，多路摄像头组成智能网络，管的是一片楼面或一个园，而不是盯一个屏</li>
  <li><strong>AIoT 智能全栈一体机（full）</strong>：企业机房、行业交付现场、大型项目一体机柜——不想拼装多套系统，需要一台机器就吃透「设备接入—视频监控—算法推理—业务管控」全链路，路数多、并发高、能力要全，长期稳跑、持续扩展</li>
</ul>

<p style="font-size: 14px; line-height: 1.8; color: #444; margin: 12px 0;">
<strong>怎么选：</strong>不必研究架构，<strong>看你现场是什么形态</strong>——单点用边缘盒子选 mini，多路 AI 摄像头上墙组网选 standard，需要全栈能力一箱配齐选 full。先拿盒子试点、再摄像头上墙铺开、最后用 AIoT 智能全栈一体机统一纳管，换硬件只换档位，平台自动适配，日常启停无需重复配置。
</p>

<p style="font-size: 14px; line-height: 1.8; color: #444; margin: 16px 0 8px 0;">
<strong>安装选型与资源符合性（实测）：</strong>
</p>

<div align="center" style="margin: 12px 0;">
  <img src=".image/deploy-profile-menu.png" alt="部署选型" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
  <p style="font-size: 13px; color: #888; margin: 8px 0 16px 0;">边缘盒子 / AI 一体摄像头 / AIoT 智能全栈一体机——按现场硬件形态选一档</p>
</div>

<div align="center" style="margin: 12px 0;">
  <img src=".image/deploy-profile-mini.png" alt="mini 实测符合性" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
  <p style="font-size: 13px; color: #888; margin: 8px 0 4px 0;"><strong>边缘盒子（mini）</strong>：4 GB 盒子实测仅需约 2 GB，单点摆上就能安心跑智能</p>
</div>

<div align="center" style="margin: 12px 0;">
  <img src=".image/deploy-profile-standard.png" alt="standard 实测符合性" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
  <p style="font-size: 13px; color: #888; margin: 8px 0 4px 0;"><strong>AI 一体摄像头（standard）</strong>：16 GB 主机实测约 10 GB，多路摄像头组网覆盖楼面/园区仍有余量</p>
</div>

<div align="center" style="margin: 12px 0;">
  <img src=".image/deploy-profile-full.png" alt="full 实测符合性" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
  <p style="font-size: 13px; color: #888; margin: 8px 0 16px 0;"><strong>AIoT 智能全栈一体机（full）</strong>：20 GB 一体机实测约 14 GB，全栈能力一箱配齐仍留足余量，可放心投产</p>
</div>

#### 🧠 AI能力

<ul style="font-size: 14px; line-height: 1.8; color: #444; margin: 10px 0;">
  <li><strong>YOLO26 新一代目标检测能力</strong>：平台内置最新一代目标检测能力，开箱即可用于实时画面分析与抓拍识别，在相同硬件条件下可接入更多路摄像头、响应更快、误报更少。支持从数据采集、标注、训练到上线推理的完整闭环，帮助用户以更低成本持续迭代专属检测模型，快速覆盖安全帽佩戴、人员闯入、烟火隐患等各类常见安防与工业场景，让「看得准、算得快、扩得动」成为默认可用能力</li>
  <li><strong>多协议摄像头接入支持</strong>：全面支持 GB28181 和 ONVIF 两大主流视频监控协议，实现标准化设备接入与管理。GB28181 作为中国国家标准，完美适配国内主流监控设备；ONVIF 作为国际通用标准，广泛兼容全球主流品牌摄像头。通过双协议支持，平台能够无缝对接现有监控系统，实现设备的即插即用、自动发现与统一管理，大幅降低设备接入门槛，提升系统兼容性与扩展性，为大规模摄像头部署提供坚实的技术基础。此外，新增 NVR 同网段/跨网段批量扫描、注册与统一管控能力，覆盖海康、大华、华为、萤石、小米等主流品牌，支持基于设备原生协议的网段发现、一键登记及通道批量导入，进一步降低大规模监控设备的接入与运维成本</li>
  <li><strong>可编排算法后处理</strong>：突破「只能检出、难以研判」的能力瓶颈，在目标检测之上增设独立的业务研判层，将画面感知结果转化为可运营、可追责、可统计的业务事件。支持按任务灵活定义人数统计、越线通行、停留超时、区域滞留、多条件复合告警等场景规则，无需反复调整模型即可快速适配工地安监、园区安防、交通管控等差异化需求，把通用视觉能力锻造成贴近现场的管理抓手。后处理与实时分析彼此独立、并行运转——监控画面持续流畅研判，业务逻辑按需弹性扩展，研判结果自动沉淀存档并驱动精准告警，显著降低误报漏报与人工复核成本。业务人员专注规则表达，平台负责分发执行与规模承载，让「看得见」真正走向「判得清、管得住、用得起来」</li>
  <li><strong>多中心节点 × 多工作节点联邦集群</strong>：面向跨区域、多机房与云边协同部署，平台采用「N 个中心节点 + N 个工作节点」联邦架构——以中心节点为统一控制面、工作节点为算力与媒体执行面，构建可横向扩展的分布式调度体系。每个中心节点纳管本域工作节点集群，支持监测代理、分布式存储、流媒体引擎、音视频转码、视频分析运行时、模型推理与训练等运行时分发与一键远程部署；多中心节点可互联同步，集群泳道视图直观呈现「中心—工作」拓扑与资源水位，支持泳道级批量维护与组件分发。算法任务、自动标注流水线、推流转发等工作负载按节点角色与 GPU 能力智能调度、队列弹性分发，让海量路数接入、高并发推理与分布式训练在同一集群中协同运转，真正做到「纳得进、分得清、扩得开、管得全」</li>
  <li><strong>SAM 零启动自动标注编排流水线</strong>：面向「尚无标注样本、尚无可用检测模型」的冷启动场景，平台集成 SAM 开放词汇分割能力与智能编排引擎，提供一键无人值守标注流水线。系统按策略自动串联摄像头抽帧采集、SAM 文本提示首批标注、达标后自动触发 YOLO 微调训练、量产阶段以 YOLO 高速推理为主并对漏检样本智能切换 SAM 回补、按进度周期性迭代训练及数据集自动打包导出，完整贯通「采—标—训—导」闭环。编排中枢实时感知流水线阶段与标注进度，自主决策 SAM / YOLO / 混合补充等标注模式及训练触发时机，支持任务暂停恢复与本地/集群算力队列弹性调度；配合可视化策略配置与运行日志，帮助用户从零样本、零模型起步快速沉淀专属检测能力，让「开口定义类别、坐等模型成型」成为数据集建设的默认可用路径</li>
  <li><strong>万级弹性算力集群与横向扩容池</strong>：面向超大规模 AI 与视频业务，构建云边端一体的分布式算力底座，将算法任务、推流转发、算法服务、模型训练与推理统一纳入横向负载均衡与弹性伸缩体系。新增服务器一键纳管入网即可成为可调度算力单元，调度中枢按资源水位与业务压力自动分发任务、平衡负载，实现从百路到万路摄像头、从单机到万级节点的线性扩容——无需重复部署与手工调参，让海量路数接入、高并发推理与分布式训练在同一算力池中协同运行，真正做到「扩得动、跑得稳、管得住」</li>
  <li><strong>天地图空间可视化与以图研判</strong>：接入国家天地图，将摄像头、告警与人车识别能力汇聚到一张地图，让监控从「看画面」升级为「看全局」。流媒体与告警模块均提供「地图分布」视图，配合设备目录树按区域聚焦，一眼掌握卡口布局与在线状态；支持地图点选、地点搜索与批量导入坐标，国标通道、NVR 通道与直连摄像头均可快速完成布点，让每路画面都有清晰的空间归属。告警事件自动关联摄像头坐标上图展示，可按时间、事件类型、任务与业务标签筛选，选中即可查看抓拍与录像，帮助值守人员从「哪里出事」快速切入处置。结合人脸库与车牌库识别能力，可将同一目标在多个点位上的命中记录串联成空间脉络——<strong>以人寻迹</strong>，还原重点人员在布控范围内的出现路线与活动范围；<strong>以车寻迹</strong>，串联过车记录，快速定位车辆行经路径与停留区域，为寻人找车、巡防布控与事后复盘提供直观线索。移动类设备还支持轨迹回放，按时间轴重现巡逻与行进路线；矢量地图与卫星影像随心切换，自动适应视野，让管理者以地图为纲、以图为媒，更快发现异常、锁定目标、指挥调度</li>
  <li><strong>Dify 智能体编排集成</strong>：集成开源 LLM 应用平台 Dify，提供可视化工作流编排、智能体（Agent）构建、知识库 RAG 检索增强及对话/工具型应用发布等能力。平台通过中间件脚本一键部署 Dify 服务，并自动完成初始化配置；同时与 GPUStack 算力层深度打通，支持将已部署的 Qwen、DeepSeek 等大语言模型作为模型供应商无缝接入智能体链路，使业务人员以低代码方式快速搭建面向安防巡检、事件研判、运维问答等场景的行业智能应用，显著缩短从模型上线到业务落地的周期</li>
  <li><strong>Qwen / DeepSeek 多卡部署</strong>：支持将 Qwen、DeepSeek 等大语言模型以多卡并行方式部署上线，可按集群与 Worker 维度灵活调度 GPU 算力，实现模型实例的弹性扩缩与负载均衡，满足高并发推理与长上下文场景下的稳定服务能力</li>
  <li><strong>算力与资源全景监测</strong>：集成 GPUStack 资源管控能力，对服务器 GPU、CPU、内存、存储等关键指标进行统一采集与可视化展示，实时掌握算力占用、显存余量、磁盘与内存使用情况，为模型部署、训练任务与视频分析链路提供可观测、可预警的运维底座</li>
  <li><strong>视觉大模型智能理解</strong>：集成QwenVL3视觉大模型，支持对实时视频画面进行深度视觉推理与语义理解，能够对画面内容进行智能分析与场景理解，提供更丰富的视觉认知能力，实现从像素级感知到语义级理解的跨越</li>
  <li><strong>摄像头实时画面 AI 分析</strong>：面向 RTSP/RTMP 实时视频流构建「拉流解码 → 智能抽帧 → 模型推理 → 结构化出数 → 告警联动」全链路分析管线，以毫秒级响应将画面变化即时转化为可检索、可研判的结构化检测事件。观看链路与算法链路架构解耦、分级码率与多卡 GPU 协同调度并重，兼顾预览清晰度与高路数并发吞吐；分析结果可无缝衔接检测区域、布防时段、人脸/车牌识别及可编排后处理规则，将传统「人盯屏、事后翻」的值守模式升级为「机器全时盯、异常秒推送、证据自动留」，让实时视频从被动观看真正变为主动感知与智能研判的基础设施</li>
  <li><strong>摄像头智能巡检</strong>：面向路数多、值守人力有限的监控场景，提供分屏巡检与设备目录批量巡检能力，在有限并发连接下对大规模摄像头进行轮巡式 AI 分析。支持轮询、连接池、混合三种调度模式——可按设定间隔自动抓拍、运行检测模型并联动告警与人脸/车牌识别；混合模式下焦点路常驻盯防、背景路池化轮巡，兼顾重点布控与全域覆盖。巡检进度实时推送，抓拍帧自动入库留存，支持从分屏画面或设备目录一键拉起数百路巡检会话，以「少连接、广覆盖、快发现」的方式，将传统人工逐屏翻看的值守模式升级为智能化自动巡检</li>
  <li><strong>云边端一体算法预警监控大屏</strong>：提供统一的云边端一体化算法预警监控大屏，实时展示设备状态、算法任务运行情况、告警事件统计、视频流分析结果等关键信息，支持多维度数据可视化展示，实现云端、边缘端、设备端的统一监控与管理，为决策者提供全局视角的智能监控指挥中心</li>
  <li><strong>人脸识别与人脸库管理能力</strong>：支持在摄像头任务中灵活开启人脸识别能力，基于Milvus构建人脸库与人脸特征向量管理体系，提供人脸样本/特征的新增、查询、更新、删除与向量检索能力。支持对抓拍画面进行高效人脸比对与身份检索，完整记录匹配结果、抓拍图片、摄像头位置信息与设备上下文，便于后续人员轨迹追溯、安防取证与多维度统计分析</li>
  <li><strong>车牌识别与车牌库管理能力</strong>：支持在监控任务中一键启用车牌识别，自动从过车画面中识别车牌信息，并与自建车牌库实时比对。可灵活维护白名单、黑名单及业务标签，车辆命中规则时即时告警联动，帮助实现出入口通行管控、重点车辆布控、访客与固定车辆分类管理等需求。支持自动收录新出现车牌、完整留存抓拍与匹配记录，便于事后查车、轨迹核对与证据留存；识别过程与原有视频分析并行运行，不影响监控与告警主流程的稳定性和实时性</li>
  <li><strong>设备检测区域绘制</strong>：提供可视化的设备检测区域绘制工具，支持在设备抓拍图片上绘制四边形和多边形检测区域，支持区域与算法模型灵活关联配置，支持区域的可视化管理、编辑、删除等操作，支持快捷键操作提升绘制效率，实现精准的区域检测配置，为算法任务提供精确的检测范围定义</li>
  <li><strong>智能联动告警机制</strong>：支持检测区域、布防时段和事件告警的三重联动机制，系统会智能判断检测到的事件是否同时满足指定的检测区域范围、处于布防时段内且匹配告警事件类型，只有同时满足这三个条件时才会触发告警，实现精准的时空条件过滤，大幅降低误报率，提升告警系统的准确性和实用性</li>
  <li><strong>大规模摄像头管理</strong>：支持百级摄像头接入，提供采集、标注、训练、推理、导出、分析、告警、录像、存储、部署等全流程服务</li>
  <li><strong>算法任务管理</strong>：支持创建和管理两种类型的算法任务，每个算法任务可灵活绑定抽帧器和排序器，实现精准的视频帧提取与结果排序
    <ul style="margin: 5px 0; padding-left: 20px;">
      <li><strong>实时算法任务</strong>：用于实时画面分析，支持RTSP/RTMP流实时处理，提供毫秒级响应能力，适用于监控、安防等实时场景</li>
      <li><strong>抓拍算法任务</strong>：用于抓拍图像分析，对抓拍图片进行智能识别与分析，适用于事件回溯、图像检索等场景</li>
    </ul>
  </li>
  <li><strong>数据集标注与多格式数据集管理</strong>：内置可视化图像标注工作台，支持矩形框、多边形等标注形态，以及标注类别管理与进度跟踪；全面兼容 YOLO、COCO、ImageFolder 等主流数据集格式的灵活导入与导出，并打通云平台数据集通道，支持云端数据集的一键导入与同步导出，贯通「数据采集—人工标注—模型训练—部署推理」全流程闭环</li>
  <li><strong>推流转发</strong>：支持在无需启用AI分析功能的情况下，直接观看摄像头实时画面。通过创建推流转发任务，可将多路摄像头进行批量推送，实现多路视频流的同步观看，满足纯视频监控场景需求</li>
  <li><strong>GPU 探测、负载分配与多卡协同</strong>：平台具备 GPU 资源探测与智能分配能力，可自动识别可用 GPU 数量，并依据各卡实时负载将视频编解码与算法推理任务动态调度到多卡并行执行，在保障稳定性的前提下提升多路流处理吞吐与算力利用率，实现多卡场景下的画面编解码与模型推理协同</li>
  <li><strong>智能传输协议与拉流高可靠</strong>：在 RTSP 等拉流链路上，系统可根据 URL/路径等条件对传输层协议进行动态判断与切换；默认对摄像头拉流采用 UDP 传输以降低时延。当连续多帧出现灰屏、解码异常或流塌缩（解码失败导致画面停滞）时，自动触发 RTSP 重连与链路恢复，降低长时间花屏、卡死对业务的影响</li>
  <li><strong>观看链路与算法链路分离及分级码率</strong>：将「实时预览/大屏观看」与「算法分析抽帧」在数据链路与控制策略上解耦，由两套独立控制面分别管理。观看侧采用约 6500 Kbps 码率，优先保障画清晰、少卡顿的监控观感；算法侧采用约 3500 Kbps 码率，在检测精度与算力/带宽占用之间取得平衡，避免分析任务与观看任务争抢同一条高码率通道，从架构上保障「看得清、不卡断」与「算得动、可扩展」兼顾</li>
  <li><strong>模型服务集群推理</strong>：支持分布式模型推理服务集群，实现智能负载均衡、故障自动切换与高可用保障，大幅提升推理吞吐量与系统稳定性</li>
  <li><strong>布防时段管理</strong>：支持全防模式和半防模式两种布防策略，可灵活配置不同时段的布防规则，实现精准的时段化智能监控与告警</li>
  <li><strong>OCR与语音识别</strong>：基于PaddleOCR实现高精度文字识别，支持语音转文本功能，提供多语言识别能力</li>
  <li><strong>多模态视觉大模型</strong>：支持物体识别、文字识别等多种视觉任务，提供强大的图像理解与场景分析能力</li>
  <li><strong>LLM大语言模型</strong>：支持RTSP流、视频、图像、语音、文本等多种输入格式的智能分析与理解，实现多模态内容理解</li>
  <li><strong>模型部署与版本管理</strong>：支持AI模型的快速部署与版本管理，实现模型一键上线、版本回滚与灰度发布</li>
  <li><strong>多实例管理</strong>：支持多个模型实例的并发运行与资源调度，提高系统利用率与资源利用效率</li>
  <li><strong>摄像头抓拍</strong>：支持摄像头实时抓拍功能，可配置抓拍规则与触发条件，实现智能抓拍与事件记录</li>
  <li><strong>抓拍空间管理</strong>：提供抓拍图片的存储空间管理，支持空间配额与清理策略，确保存储资源合理利用</li>
  <li><strong>录像空间管理</strong>：提供录像文件的存储空间管理，支持自动清理与归档，实现存储资源的智能管理</li>
  <li><strong>抓拍图片管理</strong>：支持抓拍图片的查看、检索、下载、删除等全生命周期管理，提供便捷的图片管理功能</li>
  <li><strong>设备目录管理</strong>：提供设备树形目录管理，支持设备分组、层级管理与权限控制，实现设备的有序组织与精细化管理</li>
  <li><strong>告警录像</strong>：支持告警事件自动触发录像功能，当检测到异常事件时自动录制相关视频片段，提供完整的告警证据链，支持告警录像的查看、下载和管理</li>
  <li><strong>告警事件</strong>：提供完整的告警事件管理功能，支持告警事件的实时推送、历史查询、统计分析、事件处理与状态跟踪，实现告警全生命周期管理</li>
  <li><strong>录像回放</strong>：支持历史录像的快速检索与回放功能，提供时间轴定位、倍速播放、关键帧跳转等便捷操作，支持多路视频同步回放，满足事件回溯与分析需求</li>
</ul>

#### 🌐 IoT能力

<ul style="font-size: 14px; line-height: 1.8; color: #444; margin: 10px 0;">
  <li><strong>设备接入与管理</strong>：设备注册、认证、状态监控、生命周期管理</li>
  <li><strong>产品与物模型管理</strong>：产品定义、物模型配置、产品管理</li>
  <li><strong>多协议支持</strong>：MQTT、TCP、HTTP等多种物联网协议</li>
  <li><strong>设备认证与动态注册</strong>：安全接入、身份认证、动态设备注册</li>
  <li><strong>规则引擎</strong>：数据流转规则、消息路由、数据转换</li>
  <li><strong>数据采集与存储</strong>：设备数据采集、存储、查询与分析</li>
  <li><strong>设备状态监控与告警管理</strong>：实时监控、异常告警、智能决策</li>
  <li><strong>通知管理</strong>：支持7种通知方式，包括飞书、钉钉、企业微信、邮件、腾讯云短信、阿里云短信、Webhook，实现灵活的多渠道告警通知</li>
</ul>

### 📦 内置 AI 模型

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
平台开箱即用，内置多种面向安防监控、工业现场、智慧交通等场景的预训练模型，可在算法任务中直接选用，快速完成部署与推理，无需从零训练即可覆盖常见视觉检测需求。
</p>

| 模型名称 | 推理格式 | 基础模型 | 能力说明 |
| :-- | :--: | :--: | :-- |
| 安全帽模型 | ONNX | YOLOv8 | 检测作业人员是否佩戴安全帽 |
| 睡岗模型 | PyTorch | YOLOv8 | 识别岗位人员睡岗、脱岗等异常行为 |
| 人模型 | PyTorch | YOLOv8 | 通用人体检测，用于画面中人员的识别与定位 |
| 车牌模型 | ONNX | YOLOv8 | 识别车辆号牌信息 |
| 反光衣模型 | PyTorch | YOLOv8 | 检测作业人员是否穿着反光衣 |
| 火焰模型 | PyTorch | YOLOv8 | 识别明火、火焰等火灾隐患 |
| 吸烟模型 | PyTorch | YOLOv8 | 识别人员吸烟行为 |
| 打电话模型 | ONNX | YOLOv8 | 识别人员打电话、使用手机等行为 |
| 道路积水模型 | ONNX | YOLOv8 | 识别道路积水、路面积水等异常状况 |
| 口罩模型 | ONNX | YOLOv8 | 检测人员是否正确佩戴口罩 |
| 跌倒检测模型 | ONNX | YOLOv8 | 识别人员跌倒等异常姿态 |
| 人脸检测模型 | ONNX | YOLOv8 | 检测画面中人脸位置，支撑人脸识别链路 |

### 💡 技术理念

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
我们认为没有任何一个编程语言能够擅长所有事情，但通过三种编程语言的深度融合，EasyAIoT将发挥各自优势，构建强大的技术生态。
</p>

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
Java擅长构建稳定可靠的平台架构，但不适合网络编程和AI编程；Python擅长网络编程和AI算法开发，但在高性能任务执行方面存在瓶颈；C++擅长高性能任务执行，但在平台开发和AI编程方面不如前两者。EasyAIoT采用三合一语言混编架构，充分发挥各语言优势，构建一个实现颇具挑战，但使用极其便捷的AIoT平台。
</p>

![EasyAIoT平台架构.jpg](.image/iframe2.jpg)

### 🔄 模块数据流转

<img src=".image/iframe3.jpg" alt="EasyAIoT平台架构" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">

### 🤖 零样本标注技术

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
创新性地依托大模型构建零样本标注技术体系（理想状态下完全去除人工标注环节，实现标注流程的自动化），该技术通过大模型生成初始数据并借助提示词技术完成自动标注，再经人机协同校验确保数据质量（可选），进而训练出初始小模型。该小模型通过持续迭代、自我优化，实现标注效率与模型精度的协同进化，最终推动系统性能不断攀升。
</p>

<img src=".image/iframe4.jpg" alt="EasyAIoT平台架构" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">

### 🏗️ 项目架构特点

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
EasyAIoT其实不是一个项目，而是六个项目。
</p>

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
好处是什么呢？假如说你在一个受限的设备上（比如RK3588），你只需要拿出其中某个项目就可以独立部署，所以看似这个项目是云平台，其实他也可以是边缘平台。
</p>

<div style="margin: 30px 0; padding: 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 10px; color: white;">

<p style="font-size: 16px; line-height: 1.8; margin: 0; font-weight: 500;">
🌟 真开源不易，如果这个项目对您有帮助，请您点亮一颗Star再离开，这将是对我最大的支持！<br>
<small style="font-size: 14px; opacity: 0.9;">（在这个假开源横行的时代，这个项目就是一个异类，纯靠爱来发电）</small>
</p>

</div>

### 🌍 本土化支持

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
EasyAIoT积极响应本土化战略，全面支持本土化硬件和操作系统，为用户提供安全可控的AIoT解决方案：
</p>

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; margin: 20px 0;">

<div style="padding: 20px; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🖥️ 服务器端支持</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>完美兼容海光（Hygon）x86架构处理器</li>
  <li>支持本土化服务器硬件平台</li>
  <li>提供针对性的性能优化方案</li>
  <li>确保企业级应用的稳定运行</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">📱 边缘端支持</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>全面支持瑞芯微（Rockchip）ARM架构芯片</li>
  <li>完美适配RK3588等主流边缘计算平台</li>
  <li>针对边缘场景进行深度优化</li>
  <li>实现边缘智能的轻量化部署</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🖱️ 操作系统支持</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>兼容麒麟（Kylin）操作系统</li>
  <li>支持方德（Founder）等本土化Linux发行版</li>
  <li>适配统信UOS等主流本土化操作系统</li>
  <li>提供完整的本土化部署方案</li>
</ul>
</div>

</div>

## 🎯 适用场景

<img src=".image/适用场景.png" alt="适用场景" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">

## 🧩 项目结构

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
EasyAIoT由六个核心项目组成：
</p>

<table style="width: 100%; border-collapse: collapse; margin: 20px 0; font-size: 14px;">
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; background-color: #f8f9fa; font-weight: 600; color: #2c3e50; width: 20%;">模块</td>
<td style="padding: 15px; border: 1px solid #e0e0e0; background-color: #f8f9fa; font-weight: 600; color: #2c3e50;">描述</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>WEB模块</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">基于Vue的前端管理界面，提供统一的用户交互体验</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>DEVICE模块</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">
  <ul style="margin: 5px 0; padding-left: 20px;">
    <li><strong>技术优势</strong>：基于JDK21，提供更好的性能和现代化特性</li>
    <li><strong>设备管理</strong>：设备注册、认证、状态监控、生命周期管理</li>
    <li><strong>产品管理</strong>：产品定义、物模型管理、产品配置</li>
    <li><strong>协议支持</strong>：MQTT、TCP、HTTP等多种物联网协议</li>
    <li><strong>设备认证</strong>：设备动态注册、身份认证、安全接入</li>
    <li><strong>规则引擎</strong>：数据流转规则、消息路由、数据转换</li>
    <li><strong>数据采集</strong>：设备数据采集、存储、查询与分析</li>
    <li><strong>节点控制面</strong>：内置 <code>iot-node</code> 微服务，提供计算/媒体节点 CRUD、SSH 连通测试、Agent 注册与心跳、工作负载调度与媒体节点池分配等统一控制面能力</li>
  </ul>
</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>NODE模块</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">
  <ul style="margin: 5px 0; padding-left: 20px;">
    <li><strong>节点代理</strong>：基于 Python 的边缘/远程节点 Agent，通过 <code>install.sh</code> 一键安装为 systemd 服务，部署于目标服务器后自动接入平台</li>
    <li><strong>控制面通信</strong>：向 <code>iot-node</code> 控制面注册并周期性心跳，实时上报 CPU、内存、磁盘、GPU 利用率及在运工作负载状态</li>
    <li><strong>远程工作负载</strong>：通过 HTTP 接口（默认 9100 端口）接收控制面下发的部署/停止指令，在节点本地拉起 AI 模型服务、算法任务、FFmpeg 转码等工作负载</li>
    <li><strong>媒体节点池</strong>：支持在节点上远程 <code>docker compose</code> 部署 SRS/ZLM 流媒体栈，配合控制面实现设备与媒体节点的 Sticky 绑定与流地址生成</li>
    <li><strong>节点角色</strong>：支持 compute（算力）、media（媒体）、hybrid（混合）三种角色，支撑 AI 推理、算法任务与流媒体业务的跨节点调度与弹性扩容</li>
    <li><strong>离线友好</strong>：提供 pip wheels 离线依赖打包与 Agent 热更新能力，适配无外网或受限网络环境下的批量节点纳管</li>
  </ul>
</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>VIDEO模块</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">
  <ul style="margin: 5px 0; padding-left: 20px;">
    <li><strong>流媒体处理</strong>：支持RTSP/RTMP流实时处理与传输</li>
    <li><strong>算法任务管理</strong>：支持实时算法任务和抓拍算法任务两种类型，分别用于实时画面分析和抓拍图像分析</li>
    <li><strong>抽帧器与排序器</strong>：支持灵活的抽帧策略与结果排序机制，每个算法任务可绑定独立的抽帧器和排序器</li>
    <li><strong>布防时段</strong>：支持全防模式和半防模式的时段化配置</li>
  </ul>
</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>AI模块</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">
  <ul style="margin: 5px 0; padding-left: 20px;">
    <li><strong>智能分析</strong>：负责视频分析和AI算法执行</li>
    <li><strong>模型服务集群</strong>：支持分布式模型推理服务，实现负载均衡与高可用</li>
    <li><strong>实时推理</strong>：提供毫秒级响应的实时智能分析能力</li>
    <li><strong>模型管理</strong>：支持模型部署、版本管理与多实例调度</li>
  </ul>
</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>TASK模块</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">基于C++的高性能任务处理模块，负责计算密集型任务执行</td>
</tr>
</table>

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
如需深入了解各模块技术栈、微服务拆分、中间件拓扑与数据流转细节，请参阅 <a href=".doc/架构设计/项目架构设计分析.md" style="color: #3498db; text-decoration: none; font-weight: 600;">项目架构设计分析</a>。
</p>

## 🖥️ 跨平台部署优势

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
EasyAIoT支持在Linux、Mac、Windows三大主流操作系统上部署，为不同环境下的用户提供灵活便捷的部署方案：
</p>

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; margin: 20px 0;">

<div style="padding: 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🐧 Linux部署优势</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>适合生产环境，稳定可靠，资源占用低</li>
  <li>支持Docker容器化部署，一键启动所有服务</li>
  <li>完美适配服务器、边缘计算设备（如RK3588等ARM架构设备）</li>
  <li>提供完整的自动化安装脚本，简化部署流程</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🍎 Mac部署优势</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>适合开发测试环境，与macOS系统深度集成</li>
  <li>支持本地开发和调试，快速验证功能</li>
  <li>提供便捷的安装脚本，支持Homebrew等包管理器</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🪟 Windows部署优势</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>适合Windows服务器环境，降低学习成本</li>
  <li>支持PowerShell自动化脚本，简化部署操作</li>
  <li>兼容Windows Server和桌面版Windows系统</li>
  <li>提供图形化安装向导，用户友好</li>
</ul>
</div>

</div>


<p style="font-size: 14px; line-height: 1.8; color: #2c3e50; font-weight: 500; margin: 20px 0; padding: 15px; background-color: #e8f4f8; border-left: 4px solid #3498db; border-radius: 4px;">
<strong>统一体验</strong>：无论选择哪种操作系统，EasyAIoT都提供一致的安装脚本和部署文档，确保跨平台部署体验的一致性。
</p>

## ☁️ EasyAIoT = AI + IoT = 云边端一体化解决方案

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
支持上千种垂直场景，支持AI模型定制化和AI算法定制化开发，深度融合。
</p>

<div style="background-color: #f8f9fa; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #3498db;">
<h3 style="color: #2c3e50; margin-top: 0;">赋能万物智视：EasyAIoT</h3>
<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 10px 0;">
构筑了物联网设备（尤其是海量摄像头）的高效接入与管控网络。我们深度融合流媒体实时传输技术与前沿人工智能（AI），打造一体化服务核心。这套方案不仅打通了异构设备的互联互通，更将高清视频流与强大的AI解析引擎深度集成，赋予监控系统"智能之眼"——精准实现人脸识别、异常行为分析、风险人员布控及周界入侵检测。
</p>
<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 10px 0;">
平台支持两种类型的算法任务：实时算法任务用于RTSP/RTMP流的实时画面分析，提供毫秒级响应能力；抓拍算法任务用于抓拍图像的智能分析，支持事件回溯与图像检索。通过算法任务管理实现灵活的抽帧与排序策略，每个任务可绑定独立的抽帧器和排序器，结合模型服务集群推理能力，确保毫秒级响应与高可用保障。同时，提供全防模式和半防模式两种布防策略，可根据不同时段灵活配置监控规则，实现精准的时段化智能监控与告警。
</p>
<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 10px 0;">
在物联网设备管理方面，EasyAIoT提供完整的设备生命周期管理能力，支持多种物联网协议（MQTT、TCP、HTTP），实现设备的快速接入、安全认证、实时监控和智能控制。通过规则引擎实现设备数据的智能流转与处理，结合AI能力对设备数据进行深度分析，实现从设备接入、数据采集、智能分析到决策执行的全流程自动化，真正实现万物互联、万物智控。
</p>
</div>

<img src=".image/iframe1.jpg" alt="EasyAIoT平台架构" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15);">

## ⚠️ 免责声明

EasyAIoT是一个开源学习项目，与商业行为无关。用户在使用该项目时，应遵循法律法规，不得进行非法活动。如果EasyAIoT发现用户有违法行为，将会配合相关机关进行调查并向政府部门举报。用户因非法行为造成的任何法律责任均由用户自行承担，如因用户使用造成第三方损害的，用户应当依法予以赔偿。使用EasyAIoT所有相关资源均由用户自行承担风险.

## 📚 部署文档

- [平台部署文档](.doc/部署文档/平台部署文档_zh.md) — Linux / Mac / Windows 分步部署指南
- [部署最佳实践](.doc/部署文档/部署最佳实践.md) — 环境要求、一键部署流程、运维排错与生产环境建议

## 🎮 演示环境

- 演示地址：http://36.111.47.113:8888/
- 账号：admin
- 密码：admin123

## ⚙️ 项目地址

- Gitee: https://gitee.com/soaring-xiongkulu/easyaiot
- Github: https://github.com/soaring-xiongkulu/easyaiot

## 📸 截图
<div>
  <img src=".image/banner/banner-video1000.gif" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner-video1001.gif" alt="Screenshot 1" width="49%">
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
  <img src=".image/banner/banner1006.jpg" alt="Screenshot 3" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1009.jpg" alt="Screenshot 1" width="49%">
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
  <img src=".image/banner/banner1117.png" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1118.png" alt="Screenshot 1" width="49%">
</div>
<div>
  <img src=".image/banner/banner1119.png" alt="Screenshot 1" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1120.png" alt="Screenshot 1" width="49%">
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
  <img src=".image/banner/banner1113.png" alt="Screenshot 8" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1114.png" alt="Screenshot 7" width="49%">
</div>
<div>
  <img src=".image/banner/banner1115.png" alt="Screenshot 8" width="49%" style="margin-right: 10px">
  <img src=".image/banner/banner1116.png" alt="Screenshot 7" width="49%">
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

## 📞 联系方式

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
请先关注下方公众号，再通过技术交流群或微信号与我们联系。
</p>

## 👥 公众号

<div>
  <img src=".image/公众号.jpg" alt="公众号" width="30%">
</div>

## 💬 技术交流群

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
关注公众号后，使用微信扫描下方二维码加入 EasyAIoT 技术交流群。
</p>

<div>
  <img src=".image/交流群3群.jpg" alt="EasyAIoT技术交流3群" width="30%">
</div>

## 💬 微信号联系

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
关注公众号后，如需一对一沟通，可扫描下方二维码添加微信好友。
</p>

<div>
  <img src=".image/微信联系方式.jpg" alt="微信号联系方式" width="200">
</div>

## 🪐 知识星球：

<p>
  <img src=".image/知识星球.jpg" alt="知识星球" width="30%">
</p>

## 💰 打赏赞助

<div>
    <img src=".image/微信支付.jpg" alt="微信支付" width="30%" height="30%">
    <img src=".image/支付宝支付.jpg" alt="支付宝支付" width="30%" height="10%">
</div>

## 🤝 贡献指南

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
我们欢迎所有形式的贡献！无论您是代码开发者、文档编写者，还是问题反馈者，您的贡献都将帮助 EasyAIoT 变得更好。以下是几种主要的贡献方式：
</p>

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; margin: 20px 0;">

<div style="padding: 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">💻 代码贡献</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>Fork 项目到您的 GitHub/Gitee 账号</li>
  <li>创建特性分支 (git checkout -b feature/AmazingFeature)</li>
  <li>提交更改 (git commit -m 'Add some AmazingFeature')</li>
  <li>推送到分支 (git push origin feature/AmazingFeature)</li>
  <li>提交 Pull Request</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">📚 文档贡献</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>完善现有文档内容</li>
  <li>补充使用示例和最佳实践</li>
  <li>提供多语言翻译</li>
  <li>修正文档错误</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🌟 其他贡献方式</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>报告并修复 Bug</li>
  <li>提出功能改进建议</li>
  <li>参与社区讨论，帮助其他开发者</li>
  <li>分享使用经验和案例</li>
</ul>
</div>

</div>

## 🌟 重大贡献者

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
以下是对EasyAIoT项目做出重大贡献的杰出贡献者，他们的贡献对项目的发展起到了关键推动作用，我们表示最诚挚的感谢！
</p>

<table style="width: 100%; table-layout: fixed; border-collapse: collapse; margin: 20px 0; font-size: 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
<thead>
<tr style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white;">
<th style="padding: 15px; text-align: left; font-weight: 600; border: 1px solid #e0e0e0; width: 32%; min-width: 9rem;">贡献者</th>
<th style="padding: 15px; text-align: left; font-weight: 600; border: 1px solid #e0e0e0;">贡献内容</th>
</tr>
</thead>
<tbody>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>℡夏别</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">为推动EasyAIoT项目贡献Windows部署文档，为Windows平台用户提供了完整的部署指南，大大降低了Windows环境下的部署难度，让更多用户能够便捷地使用EasyAIoT平台。</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>YiYaYiYaho</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">为推动EasyAIoT项目贡献Mac容器一键部署脚本，为Mac平台用户提供了自动化部署解决方案，显著简化了Mac环境下的部署流程，提升了开发者和用户的部署体验。</td>
</tr>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>山寒</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">为推动EasyAIoT项目贡献Linux容器部署脚本，为Linux平台用户提供了容器化部署方案，实现了快速、可靠的容器部署，为生产环境的稳定运行提供了重要保障。</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>玖零。</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">为推动EasyAIoT项目贡献Linux容器部署脚本，进一步完善了Linux平台的容器化部署方案，为不同Linux发行版用户提供了更多选择，推动了项目的跨平台部署能力。</td>
</tr>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>爱吃小柚子</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">为推动EasyAIoT项目在视频监控与智能分析方向的发展，主导并完成国标 GB28181 与 AI 业务流程的端到端联调与验证测试；同时承担画面清晰度与播放流畅度的专项测试与评估，为国标接入可靠性、链路稳定性以及视频观感体验的持续优化提供了重要依据。</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>Dark</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">为推动EasyAIoT项目在国标视频监控方向的发展，贡献 GB28181 能力的端到端打通，实现视频播放与云台控制，使国标设备接入具备可用的实况预览与远程操控能力。</td>
</tr>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>machh</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">为推动 EasyAIoT-Edge 项目的发展，完成摄像头接入与 AI 能力的端到端跑通，并实现功能串联，使边缘侧「接入—智能分析」链路可用、可闭环。</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>遗忘的星空</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">为推动 EasyAIoT 项目在设备直连接入方向的发展，贡献多品牌 IP 摄像头资产盘点与网段扫描能力，支持海康 IPC、NVR 等设备的批量发现与识别；完善直连设备在同网段、跨网段场景下的批量搜索与一键注册流程，基于设备原生协议实现接入，可绕过海康 SDK、摆脱对海康平台的强依赖，为开放、可控的摄像头规模化接入奠定了基础。</td>
</tr>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>阿龙</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">为推动 EasyAIoT 项目在地图可视化与空间研判方向的发展，独立贡献天地图空间可视化能力的完整代码实现，涵盖国家天地图底图接入、摄像头与告警设备布点、地图分布视图、地点搜索与坐标批量导入、告警事件自动上图、以人/以车寻迹及移动设备轨迹回放等核心链路，使平台「天地图空间可视化与以图研判」能力从方案设计真正走向可落地、可使用的生产形态。</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>雨落流殇</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">为推动 EasyAIoT 项目在超大规模流媒体承载方向的发展，贡献 SRS 与 ZLMediaKit 异构流媒体服务器集群的部署架构与调度思路，提出多节点池协同、流媒体控制面与业务层解耦、存储与上传流水线及节点注册调度等可扩展方案，为平台支撑万级路摄像头并发接入、稳定分发与弹性扩容奠定了重要的架构基础。</td>
</tr>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>常康</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">为推动 EasyAIoT 项目在智能交通与车辆管控方向的发展，独立贡献车牌识别算法与完整代码实现，涵盖基于 ONNX 的车牌定位检测、号码与颜色识别、双层牌拼接与倾斜透视校正、车牌库管理与多库顺序匹配、算法任务一键联动及 Kafka 异步比对等核心链路，全面支持蓝/黄/绿/白牌及新能源车牌等主流类型，使平台「车牌识别与车牌库管理能力」从能力规划真正走向可落地、可闭环的生产应用。</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>Li</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">为推动 EasyAIoT 项目在青年开发者社群与协作生态方向的发展，以卓越的组织领导力与感召力，带领全校同学深度参与项目共建，汇聚青春才智、凝聚团队合力，为 EasyAIoT 注入了源源不断、绵延不绝的发展动能；在项目传播推广、实践落地与后续人才梯队培育等方面，亦作出了举足轻重、不可替代的重要贡献。</td>
</tr>
</tbody>
</table>

<p style="font-size: 14px; line-height: 1.8; color: #2c3e50; font-weight: 500; margin: 20px 0; padding: 15px; background-color: #e8f4f8; border-left: 4px solid #3498db; border-radius: 4px;">
<strong>特别致谢</strong>：以上贡献者在跨平台部署文档与脚本、国标视频能力落地与 AI 联调验证、多品牌摄像头直连发现与批量接入、天地图空间可视化完整落地、异构流媒体集群部署与调度架构、车牌识别算法与完整代码落地、EasyAIoT-Edge 边缘侧端到端串联、校园开发者社群组织与青年协作生态构建等不同方面推动了 EasyAIoT 的发展，他们的专业精神与无私奉献值得我们学习与尊敬。再次向这些杰出的贡献者表示最诚挚的感谢！🙏
</p>

## 💝 开源守望者

开源项目的持续推进，从来不只依赖代码与文档。在 EasyAIoT 算力资源最吃紧、项目几近难以为继的那些日子里，正是以下各位以真金白银的支持，为项目注入了最关键的续航——你们或许未曾提交一行代码，但每一份信任与托举，都让这个项目得以跨过最难的槛、继续向前迭代。只要有人在用、有人在撑，开源生态便值得走得更远；EasyAIoT 今日所能抵达的高度，离不开这些在关键时刻雪中送炭的同行者。我们向每一位给予援手的朋友致以最诚挚的敬意与感谢！以下排名不分先后：

<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/阿涛.png" width="80px;" alt="阿涛"/><br /><sub><b>阿涛</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/火车叨位去、.png" width="80px;" alt="火车叨位去、"/><br /><sub><b>火车叨位去、</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/NULL.png" width="80px;" alt="NULL"/><br /><sub><b>NULL</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/一片天.png" width="80px;" alt="一片天"/><br /><sub><b>一片天</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/舍得.png" width="80px;" alt="舍得"/><br /><sub><b>舍得</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/M.png" width="80px;" alt="M"/><br /><sub><b>M</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/Louis.png" width="80px;" alt="Louis"/><br /><sub><b>Louis</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/胡首凡 梯控门禁五方对讲.png" width="80px;" alt="胡首凡 梯控门禁五方对讲"/><br /><sub><b>胡首凡 梯控门禁五方对讲</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/open-source-guardian/袁建华.png" width="80px;" alt="袁建华"/><br /><sub><b>袁建华</b></sub></a></td>
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
    </tr>
  </tbody>
</table>

## 🏆 最佳实践者

他们是将 EasyAIoT 从「可用」推向「好用、用好」的先行者——以下各位已完成 EasyAIoT 项目部署或业务场景落地，其探索与成果为社区树立了可复制、可参考的标杆，我们向这些卓越践行者致以崇高敬意与衷心祝贺！以下排名不分先后：

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

## 🙏 致谢

感谢以下各位对本项目包括但不限于代码贡献、问题反馈、资金捐赠等各种方式的支持！以下排名不分先后：
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/陈勇至.jpg" width="80px;" alt="陈勇至"/><br /><sub><b>陈勇至</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/Dark.jpg" width="80px;" alt="Dark"/><br /><sub><b>Dark</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://github.com/machh" target="_blank"><img src="./.image/sponsor/machh.jpg" width="80px;" alt="machh"/><br /><sub><b>machh</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/三块两毛四.jpg" width="80px;" alt="三块两毛四"/><br /><sub><b>三块两毛四</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/嘎嗝.jpg" width="80px;" alt="嘎嗝"/><br /><sub><b>嘎嗝</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/曹.jpg" width="80px;" alt="曹"/><br /><sub><b>曹</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/滔滔.jpg" width="80px;" alt="滔滔"/><br /><sub><b>滔滔</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src="./.image/sponsor/物语晨水²⁰²⁶.jpg" width="80px;" alt="物语晨水²⁰²⁶"/><br /><sub><b>物语晨水²⁰²⁶</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/L_Z_M" target="_blank"><img src=".image/sponsor/玖零。.jpg" width="80px;" alt="玖零。"/><br /><sub><b>玖零。</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/36436022" target="_blank"><img src=".image/sponsor/金鸿伟.jpg" width="80px;" alt="金鸿伟"/><br /><sub><b>金鸿伟</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/cnlijf" target="_blank"><img src="./.image/sponsor/李江峰.jpg" width="80px;" alt="李江峰"/><br /><sub><b>李江峰</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="javascript:void(0)" target="_blank"><img src=".image/sponsor/Best%20Yao.jpg" width="80px;" alt="Best Yao"/><br /><sub><b>Best Yao</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/weiloser" target="_blank"><img src=".image/sponsor/无为而治.jpg" width="80px;" alt="无为而治"/><br /><sub><b>无为而治</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/shup092_admin" target="_blank"><img src="./.image/sponsor/shup.jpg" width="80px;" alt="shup"/><br /><sub><b>shup</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/gampa" target="_blank"><img src="./.image/sponsor/也许.jpg" width="80px;" alt="也许"/><br /><sub><b>也许</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/leishaozhuanshudi" target="_blank"><img src="./.image/sponsor/⁰ʚᦔrꫀꪖꪑ⁰ɞ%20..jpg" width="80px;" alt="⁰ʚᦔrꫀꪖꪑ⁰ɞ ."/><br /><sub><b>⁰ʚᦔrꫀꪖꪑ⁰ɞ .</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/fateson" target="_blank"><img src="./.image/sponsor/逆.jpg" width="80px;" alt="逆"/><br /><sub><b>逆</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/dongGezzz_admin" target="_blank"><img src="./.image/sponsor/廖东旺.jpg" width="80px;" alt="廖东旺"/><br /><sub><b>廖东旺</b></sub></a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/huangzhen1993" target="_blank"><img src="./.image/sponsor/黄振.jpg" width="80px;" alt="黄振"/><br /><sub><b>黄振</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://github.com/fengchunshen" target="_blank"><img src="./.image/sponsor/春生.jpg" width="80px;" alt="春生"/><br /><sub><b>春生</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/mrfox_wang" target="_blank"><img src="./.image/sponsor/贵阳王老板.jpg" width="80px;" alt="贵阳王老板"/><br /><sub><b>贵阳王老板</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/haobaby" target="_blank"><img src="./.image/sponsor/hao_chen.jpg" width="80px;" alt="hao_chen"/><br /><sub><b>hao_chen</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/finalice" target="_blank"><img src="./.image/sponsor/尽千.jpg" width="80px;" alt="尽千"/><br /><sub><b>尽千</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/yuer629" target="_blank"><img src="./.image/sponsor/yuer629.jpg" width="80px;" alt="yuer629"/><br /><sub><b>yuer629</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/cai-peikai/ai-project" target="_blank"><img src="./.image/sponsor/kong.jpg" width="80px;" alt="kong"/><br /><sub><b>kong</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/HB1731276584" target="_blank"><img src="./.image/sponsor/岁月静好.jpg" width="80px;" alt="岁月静好"/><br /><sub><b>岁月静好</b></sub></a></td>
      <td align="center" valign="top" width="11.11%"><a href="https://gitee.com/hy5128" target="_blank"><img src="./.image/sponsor/Kunkka.jpg" width="80px;" alt="Kunkka"/><br /><sub><b>Kunkka</b></sub></a></td>
    </tr>
    <tr>
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
    </tr>
  </tbody>
</table>

## 💡 期望

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
欢迎提出更好的意见，帮助完善 easyaiot
</p>

## 📄 版权

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
翱翔的雄库鲁/easyaiot 采用 <a href="https://gitee.com/soaring-xiongkulu/easyaiot/blob/main/LICENSE" style="color: #3498db; text-decoration: none; font-weight: 600;">MIT LICENSE</a> 开源协议。我们致力于推动 AI 技术的普及与发展，让更多人能够自由使用和受益于这项技术。
</p>

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
<strong>使用许可</strong>：个人与企业可 100% 免费使用，无需保留作者、Copyright 信息。我们相信技术的价值在于被广泛使用和持续创新，而非被版权束缚。希望您能够自由地使用、修改、分发本项目，让 AI 技术真正惠及每一个人。
</p>

## 🌟 Star增长趋势图

[![Stargazers over time](https://starchart.cc/soaring-xiongkulu/easyaiot.svg?variant=adaptive)](https://starchart.cc/soaring-xiongkulu/easyaiot)
