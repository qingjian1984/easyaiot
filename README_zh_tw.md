# EasyAIoT（雲邊端一體化智能算法應用平臺）

[![Gitee star](https://gitee.com/volara/easyaiot/badge/star.svg?theme=gvp)](https://gitee.com/soaring-xiongkulu/easyaiot/stargazers)
[![Gitee fork](https://gitee.com/volara/easyaiot/badge/fork.svg?theme=gvp)](https://gitee.com/soaring-xiongkulu/easyaiot/members)

<p style="font-size: 16px; line-height: 1.8; color: #555; font-weight: 400; margin: 20px 0;">
我希望全世界都能使用這個系統，實現AI的真正0門檻，人人都能體驗到AI帶來的好處，而並不只是掌握在少數人手裏。
</p>

<div align="center">
    <img src=".image/logo.png" width="30%" height="30%" alt="EasyAIoT">
</div>

<h4 align="center" style="display: flex; justify-content: center; gap: 20px; flex-wrap: wrap; padding: 20px; font-weight: bold;">
  <a href="./README.md">English</a>
  <span style="display: flex; align-items: center; color: #666; font-weight: bold;">|</span>
  <a href="./README_zh.md">簡體中文</a>
  <span style="display: flex; align-items: center; color: #666; font-weight: bold;">|</span>
  <a href="./README_zh_tw.md">繁體中文</a>
  <span style="display: flex; align-items: center; color: #666; font-weight: bold;">|</span>
  <a href="./README_ru.md">Русский</a>
  <span style="display: flex; align-items: center; color: #666; font-weight: bold;">|</span>
  <a href="./README_fr.md">Français</a>
  <span style="display: flex; align-items: center; color: #666; font-weight: bold;">|</span>
  <a href="./README_ko.md">한국어</a>
</h4>

## 📖 項目介紹

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
<strong>EasyAIoT</strong>（Easy AI Internet of Things）是一款<strong>雲邊端一體化的智能算法應用平臺</strong>，專注於將人工智能與物聯網深度融合，讓攝像頭、傳感器與邊緣算力在現場即可協同運轉——從設備接入、數據采集，到實時視覺分析、智能研判與告警聯動，全鏈路在一套軟件中貫通完成。
</p>

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
許多智能物聯網項目落地時面臨同一困境：視頻系統、設備平臺、算法服務各自為政，集成成本高、運維割裂、擴容困難。<strong>EasyAIoT 用一套平臺化解這一矛盾</strong>——同一套軟件既可部署在 4 GB 邊緣盒子上實現單點智能，也可搭載於 AI 一體攝像頭完成樓面級覆蓋，還能裝進企業級全棧一體機，一箱配齊 IoT 納管、海量視頻接入與 AI 分析研判，不必維護多套版本、不必反復對接異構系統。
</p>

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
平臺由 <strong>WEB、APP、DEVICE、NODE、VIDEO、AI、TASK</strong> 七個核心模塊組成，以 Java 構建穩定管控底座、Python 承載 AI 與網絡能力、C++ 驅動高性能計算任務，三語言混編各取所長。在能力側，平臺覆蓋 GB28181 / ONVIF 多協議攝像頭接入、實時與抓拍算法任務、YOLO 目標檢測與 SAM 零樣本自動標註、人臉/車牌識別、可編排業務後處理、聯邦算力集群調度，以及 MQTT / TCP / HTTP 物聯網設備全生命周期管理；在體驗側，Web 管控臺與移動 App / 小程序能力對齊，讓指揮中心與現場巡檢同一套業務邏輯、隨時隨地處置。
</p>

<p style="font-size: 14px; line-height: 1.8; color: #444; margin: 16px 0 8px 0;">
<strong>一句話概括：</strong>EasyAIoT = AI + IoT，讓萬物互聯的同時實現萬物智視、萬物智控。
</p>

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
📄 如需更完整的圖文介紹，請參閱 <a href=".doc/项目介绍/EasyAIoT项目介绍 V2.0.pptx" style="color: #3498db; text-decoration: none; font-weight: 600;">EasyAIoT 項目介紹（PDF）</a>。
</p>

## 🌟 關於項目的一些思考

### 📍 項目定位

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
EasyAIoT是一個雲邊端一體化的智能物聯網平臺，專註於AI與IoT的深度融合。平臺通過算法任務管理、實時流分析、模型服務集群推理等核心能力，實現從設備接入到數據采集、AI分析、智能決策的全鏈路閉環，真正實現萬物互聯、萬物智控。
</p>

### 🎯 三檔硬體，一套平臺

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
很多智能物聯網項目走到落地時都會卡住：<strong>功能做全了，小機器裝不下；為了裝得下，又得砍能力、拆版本、維護多套部署包。</strong> EasyAIoT 用同一套平臺化解這一矛盾——<strong>邊緣盒子點上智能、AI 一體攝像頭上牆即分析、AIoT 智能全棧一體機一箱配齊全鏈路</strong>，三類最常見的現場硬件各選一檔即可，同一套軟件貫穿從單點試點到樓面覆蓋再到全棧交付，不必拆版本。
</p>

| 選型 | 典型硬件（舉例） | 推薦內存 | 你能做到什麼 | 實測驗證 |
| :-- | :-- | :--: | :-- | :--: |
| **mini** 邊緣精簡版 | <strong>邊緣盒子</strong>（4 GB 工控機、門店安防一體機、工地現場網關） | ≥ 4 GB | <strong>一個點位裝上就有智能</strong>：攝像頭接入、實時分析、智能告警、模型推理，最低成本落地視覺能力 | 僅需約 2 GB，餘量充足 |
| **standard** 標準版 | <strong>AI 一體攝像頭</strong>（智能攝像終端、帶算力 AI 監控攝像頭、多目 AI 分析一體機） | ≥ 16 GB | <strong>每路攝像頭即智能節點</strong>：多路攝像頭上牆即可樓面/園區級覆蓋，設備、規則、算力統一編排，多場景並行運營 | 約 10 GB，運行平穩有餘量 |
| **full** 完整版（默認） | <strong>AIoT 智能全棧一體機</strong>（企業級全棧智控一體機、行業物聯網全棧主機、雲邊端一體智能平臺一體機） | ≥ 20 GB | <strong>一箱配齊 IoT + 視頻 + AI</strong>：設備納管、海量接入、智能分析、指揮研判一體化，全量能力長期穩跑 | 約 14 GB，全能力開啟仍留足餘量 |

<p style="font-size: 14px; line-height: 1.8; color: #444; margin: 16px 0 8px 0;">
<strong>安裝選型與資源符合性（實測）：</strong>
</p>

<div style="display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; margin: 12px 0;">
  <div style="flex: 1 1 22%; min-width: 160px; text-align: center;">
    <img src=".image/deploy-profile-menu.png" alt="部署選型" style="width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
    <p style="font-size: 12px; color: #888; margin: 8px 4px 0;">按現場硬件形態選一檔</p>
  </div>
  <div style="flex: 1 1 22%; min-width: 160px; text-align: center;">
    <img src=".image/deploy-profile-mini.png" alt="mini 實測符合性" style="width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
    <p style="font-size: 12px; color: #888; margin: 8px 4px 0;"><strong>邊緣盒子（mini）</strong>：實測約 2 GB，單點可安心跑智能</p>
  </div>
  <div style="flex: 1 1 22%; min-width: 160px; text-align: center;">
    <img src=".image/deploy-profile-standard.png" alt="standard 實測符合性" style="width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
    <p style="font-size: 12px; color: #888; margin: 8px 4px 0;"><strong>AI 一體攝像頭（standard）</strong>：實測約 10 GB，組網覆蓋仍有餘量</p>
  </div>
  <div style="flex: 1 1 22%; min-width: 160px; text-align: center;">
    <img src=".image/deploy-profile-full.png" alt="full 實測符合性" style="width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
    <p style="font-size: 12px; color: #888; margin: 8px 4px 0;"><strong>AIoT 智能全棧一體機（full）</strong>：實測約 14 GB，全棧配齊可放心投產</p>
  </div>
</div>

#### 🧠 AI能力

<ul style="font-size: 14px; line-height: 1.8; color: #444; margin: 10px 0;">
  <li><strong>YOLO26 新一代目標檢測能力</strong>：平台內建最新一代目標檢測能力，開箱即可用於即時畫面分析與抓拍識別，在相同硬體條件下可接入更多路攝像頭、響應更快、誤報更少。支持從數據採集、標註、訓練到上線推理的完整閉環，幫助用戶以更低成本持續迭代專屬檢測模型，快速覆蓋安全帽佩戴、人員闖入、煙火隱患等各類常見安防與工業場景，讓「看得準、算得快、擴得動」成為默認可用能力</li>
  <li><strong>多協議攝像頭接入支持</strong>：全面支持 GB28181 和 ONVIF 兩大主流視頻監控協議，實現標準化設備接入與管理。GB28181 作為中國國家標準，完美適配國內主流監控設備；ONVIF 作為國際通用標準，廣泛兼容全球主流品牌攝像頭。通過雙協議支持，平台能夠無縫對接現有監控系統，實現設備的即插即用、自動發現與統一管理，大幅降低設備接入門檻，提升系統兼容性與擴展性，為大規模攝像頭部署提供堅實的技術基礎。此外，新增 NVR 同網段/跨網段批量掃描、註冊與統一管控能力，覆蓋海康、大華、華為、螢石、小米等主流品牌，支持基於設備原生協議的網段發現、一鍵登記及通道批量導入，進一步降低大規模監控設備的接入與運維成本</li>
  <li><strong>可編排演算法後處理</strong>：突破「只能檢出、難以研判」的能力瓶頸，在目標檢測之上增設獨立的業務研判層，將畫面感知結果轉化為可運營、可追責、可統計的業務事件。支持按任務靈活定義人數統計、越線通行、停留超時、區域滯留、多條件複合告警等場景規則，無需反覆調整模型即可快速適配工地安監、園區安防、交通管控等差異化需求，把通用視覺能力鍛造成貼近現場的管理抓手。後處理與即時分析彼此獨立、並行運轉——監控畫面持續流暢研判，業務邏輯按需彈性擴展，研判結果自動沉澱存檔並驅動精準告警，顯著降低誤報漏報與人工複核成本。業務人員專注規則表達，平台負責分發執行與規模承載，讓「看得見」真正走向「判得清、管得住、用得起來」</li>
  <li><strong>多中心節點 × 多工作節點聯邦集群</strong>：面向跨區域、多機房與雲邊協同部署，平台採用「N 個中心節點 + N 個工作節點」聯邦架構——以中心節點為統一控制面、工作節點為算力與媒體執行面，構建可橫向擴展的分散式調度體系。每個中心節點納管本域工作節點集群，支持監測代理、分散式存儲、流媒體引擎、音視頻轉碼、視頻分析運行時、模型推理與訓練等運行時分發與一鍵遠程部署；多中心節點可互聯同步，集群泳道視圖直觀呈現「中心—工作」拓撲與資源水位，支持泳道級批量維護與組件分發。演算法任務、自動標註流水線、推流轉發等工作負載按節點角色與 GPU 能力智能調度、隊列彈性分發，讓海量路數接入、高併發推理與分散式訓練在同一集群中協同運轉，真正做到「納得進、分得清、擴得開、管得全」</li>
  <li><strong>SAM 零啟動自動標註編排流水線</strong>：面向「尚無標註樣本、尚無可用檢測模型」的冷啟動場景，平台集成 SAM 開放詞彙分割能力與智能編排引擎，提供一鍵無人值守標註流水線。系統按策略自動串聯攝像頭抽幀採集、SAM 文本提示首批標註、達標後自動觸發 YOLO 微調訓練、量產階段以 YOLO 高速推理為主並對漏檢樣本智能切換 SAM 回補、按進度週期性迭代訓練及數據集自動打包導出，完整貫通「採—標—訓—導」閉環。編排中樞即時感知流水線階段與標註進度，自主決策 SAM / YOLO / 混合補充等標註模式及訓練觸發時機，支持任務暫停恢復與本地/集群算力隊列彈性調度；配合可視化策略配置與運行日誌，幫助用戶從零樣本、零模型起步快速沉澱專屬檢測能力，讓「開口定義類別、坐等模型成型」成為數據集建設的默認可用路徑</li>
  <li><strong>萬級彈性算力集群與橫向擴容池</strong>：面向超大規模 AI 與視頻業務，構建雲邊端一體的分散式算力底座，將演算法任務、推流轉發、演算法服務、模型訓練與推理統一納入橫向負載均衡與彈性伸縮體系。新增伺服器一鍵納管入網即可成為可調度算力單元，調度中樞按資源水位與業務壓力自動分發任務、平衡負載，實現從百路到萬路攝影機、從單機到萬級節點的線性擴容——無需重複部署與手工調參，讓海量路數接入、高併發推理與分散式訓練在同一算力池中協同運行，真正做到「擴得動、跑得穩、管得住」</li>
  <li><strong>天地圖空間可視化與以圖研判</strong>：接入國家天地圖，將攝像頭、告警與人車識別能力匯聚到一張地圖，讓監控從「看畫面」升級為「看全局」。流媒體與告警模組均提供「地圖分布」視圖，配合設備目錄樹按區域聚焦，一眼掌握卡口佈局與在線狀態；支持地圖點選、地點搜索與批量導入坐標，國標通道、NVR 通道與直連攝像頭均可快速完成布點，讓每路畫面都有清晰的空間歸屬。告警事件自動關聯攝像頭坐標上圖展示，可按時間、事件類型、任務與業務標籤篩選，選中即可查看抓拍與錄像，幫助值守人員從「哪裡出事」快速切入處置。結合人臉庫與車牌庫識別能力，可將同一目標在多個點位上的命中記錄串聯成空間脈絡——<strong>以人尋跡</strong>，還原重點人員在布控範圍內的出現路線與活動範圍；<strong>以車尋跡</strong>，串聯過車記錄，快速定位車輛行經路徑與停留區域，為尋人找車、巡防布控與事後復盤提供直觀線索。移動類設備還支持軌跡回放，按時間軸重現巡邏與行進路線；矢量地圖與衛星影像隨心切換，自動適應視野，讓管理者以地圖為綱、以圖為媒，更快發現異常、鎖定目標、指揮調度</li>
  <li><strong>Qwen / DeepSeek 多卡部署</strong>：支持將 Qwen、DeepSeek 等大語言模型以多卡並行方式部署上線，可按集群與 Worker 維度靈活調度 GPU 算力，實現模型實例的彈性擴縮與負載均衡，滿足高並發推理與長上下文場景下的穩定服務能力</li>
  <li><strong>視覺大模型智能理解</strong>：集成QwenVL3視覺大模型，支持對實時視頻畫面進行深度視覺推理與語義理解，能夠對畫面內容進行智能分析與場景理解，提供更豐富的視覺認知能力，實現從像素級感知到語義級理解的跨越</li>
  <li><strong>攝像頭即時畫面 AI 分析</strong>：面向 RTSP/RTMP 即時視頻流構建「拉流解碼 → 智能抽幀 → 模型推理 → 結構化出數 → 告警聯動」全鏈路分析管線，以毫秒級響應將畫面變化即時轉化為可檢索、可研判的結構化檢測事件。觀看鏈路與演算法鏈路架構解耦、分級碼率與多卡 GPU 協同調度並重，兼顧預覽清晰度與高路數併發吞吐；分析結果可無縫銜接檢測區域、布防時段、人臉/車牌識別及可編排後處理規則，將傳統「人盯屏、事後翻」的值守模式升級為「機器全時盯、異常秒推送、證據自動留」，讓即時視頻從被動觀看真正變為主動感知與智能研判的基礎設施</li>
  <li><strong>攝像頭智能巡檢</strong>：面向路數多、值守人力有限的監控場景，提供分屏巡檢與設備目錄批量巡檢能力，在有限併發連接下對大規模攝像頭進行輪巡式 AI 分析。支持輪詢、連接池、混合三種調度模式——可按設定間隔自動抓拍、運行檢測模型並聯動告警與人臉/車牌識別；混合模式下焦點路常駐盯防、背景路池化輪巡，兼顧重點佈控與全域覆蓋。巡檢進度實時推送，抓拍幀自動入庫留存，支持從分屏畫面或設備目錄一鍵拉起數百路巡檢會話，以「少連接、廣覆蓋、快發現」的方式，將傳統人工逐屏翻看的值守模式升級為智能化自動巡檢</li>
  <li><strong>雲邊端一體算法預警監控大屏</strong>：提供統一的雲邊端一體化算法預警監控大屏，實時展示設備狀態、算法任務運行情況、告警事件統計、視頻流分析結果等關鍵信息，支持多維度數據可視化展示，實現雲端、邊緣端、設備端的統一監控與管理，為決策者提供全局視角的智能監控指揮中心</li>
  <li><strong>人臉識別與人臉庫管理能力</strong>：支持在攝像頭任務中靈活開啟人臉識別能力，基於Milvus構建人臉庫與人臉特徵向量管理體系，提供人臉樣本/特徵的新增、查詢、更新、刪除與向量檢索能力。支持對抓拍畫面進行高效人臉比對與身份檢索，完整記錄匹配結果、抓拍圖片、攝像頭位置信息與設備上下文，便於後續人員軌跡追溯、安防取證與多維度統計分析</li>
  <li><strong>車牌識別與車牌庫管理能力</strong>：支持在監控任務中一鍵啟用車牌識別，自動從過車畫面中識別車牌信息，並與自建車牌庫即時比對。可靈活維護白名單、黑名單及業務標籤，車輛命中規則時即時告警聯動，幫助實現出入口通行管控、重點車輛佈控、訪客與固定車輛分類管理等需求。支持自動收錄新出現車牌、完整留存抓拍與匹配記錄，便於事後查車、軌跡核對與證據留存；識別過程與原有視頻分析並行運行，不影響監控與告警主流程的穩定性和即時性</li>
  <li><strong>設備檢測區域繪制</strong>：提供可視化的設備檢測區域繪制工具，支持在設備抓拍圖片上繪制四邊形和多邊形檢測區域，支持區域與算法模型靈活關聯配置，支持區域的可視化管理、編輯、刪除等操作，支持快捷鍵操作提升繪制效率，實現精準的區域檢測配置，為算法任務提供精確的檢測範圍定義</li>
  <li><strong>智能聯動告警機制</strong>：支持檢測區域、布防時段和事件告警的三重聯動機制，系統會智能判斷檢測到的事件是否同時滿足指定的檢測區域範圍、處於布防時段內且匹配告警事件類型，只有同時滿足這三個條件時才會觸發告警，實現精準的時空條件過濾，大幅降低誤報率，提升告警系統的準確性和實用性</li>
  <li><strong>大規模攝像頭管理</strong>：支持百級攝像頭接入，提供采集、標註、訓練、推理、導出、分析、告警、錄像、存儲、部署等全流程服務</li>
  <li><strong>算法任務管理</strong>：支持創建和管理兩種類型的算法任務，每個算法任務可靈活綁定抽幀器和排序器，實現精準的視頻幀提取與結果排序
    <ul style="margin: 5px 0; padding-left: 20px;">
      <li><strong>實時算法任務</strong>：用於實時畫面分析，支持RTSP/RTMP流實時處理，提供毫秒級響應能力，適用於監控、安防等實時場景</li>
      <li><strong>抓拍算法任務</strong>：用於抓拍圖像分析，對抓拍圖片進行智能識別與分析，適用於事件回溯、圖像檢索等場景</li>
    </ul>
  </li>
  <li><strong>數據集標註與多格式數據集管理</strong>：內建可視化圖像標註工作台，支持矩形框、多邊形等標註形態，以及標註類別管理與進度追蹤；全面兼容 YOLO、COCO、ImageFolder 等主流數據集格式的靈活導入與導出，並打通雲平台數據集通道，支持雲端數據集的一鍵導入與同步導出，貫通「數據采集—人工標註—模型訓練—部署推理」全流程閉環</li>
  <li><strong>推流轉發</strong>：支持在無需啟用AI分析功能的情況下，直接觀看攝像頭實時畫面。通過創建推流轉發任務，可將多路攝像頭進行批量推送，實現多路視頻流的同步觀看，滿足純視頻監控場景需求</li>
  <li><strong>GPU 探測、負載分配與多卡協同</strong>：平台具備 GPU 資源探測與智能分配能力，可自動識別可用 GPU 數量，並依據各卡即時負載將視頻編解碼與算法推理任務動態調度至多卡並行執行，在保障穩定性的前提下提升多路流處理吞吐與算力利用率，實現多卡場景下的畫面編解碼與模型推理協同</li>
  <li><strong>智能傳輸協議與拉流高可靠</strong>：在 RTSP 等拉流鏈路上，系統可根據 URL/路徑等條件對傳輸層協議進行動態判斷與切換；預設對攝像頭拉流採用 UDP 傳輸以降低時延。當連續多幀出現灰屏、解碼異常或流塌縮（解碼失敗導致畫面停滯）時，自動觸發 RTSP 重連與鏈路恢復，降低長時間花屏、卡死對業務的影響</li>
  <li><strong>觀看鏈路與算法鏈路分離及分級碼率</strong>：將「即時預覽/大屏觀看」與「算法分析抽幀」在數據鏈路與控制策略上解耦，由兩套獨立控制面分別管理。觀看側採用約 6500 Kbps 碼率，優先保障畫清晰、少卡頓的監控觀感；算法側採用約 3500 Kbps 碼率，在檢測精度與算力/頻寬佔用之間取得平衡，避免分析任務與觀看任務爭搶同一條高碼率通道，從架構上保障「看得清、不卡斷」與「算得動、可擴展」兼顧</li>
  <li><strong>模型服務集群推理</strong>：支持分佈式模型推理服務集群，實現智能負載均衡、故障自動切換與高可用保障，大幅提升推理吞吐量與系統穩定性</li>
  <li><strong>布防時段管理</strong>：支持全防模式和半防模式兩種布防策略，可靈活配置不同時段的布防規則，實現精準的時段化智能監控與告警</li>
  <li><strong>OCR與語音識別</strong>：基於PaddleOCR實現高精度文字識別，支持語音轉文本功能，提供多語言識別能力</li>
  <li><strong>多模態視覺大模型</strong>：支持物體識別、文字識別等多種視覺任務，提供強大的圖像理解與場景分析能力</li>
  <li><strong>LLM大語言模型</strong>：支持RTSP流、視頻、圖像、語音、文本等多種輸入格式的智能分析與理解，實現多模態內容理解</li>
  <li><strong>模型部署與版本管理</strong>：支持AI模型的快速部署與版本管理，實現模型一鍵上線、版本回滾與灰度發布</li>
  <li><strong>多實例管理</strong>：支持多個模型實例的並發運行與資源調度，提高系統利用率與資源利用效率</li>
  <li><strong>攝像頭抓拍</strong>：支持攝像頭實時抓拍功能，可配置抓拍規則與觸發條件，實現智能抓拍與事件記錄</li>
  <li><strong>抓拍空間管理</strong>：提供抓拍圖片的存儲空間管理，支持空間配額與清理策略，確保存儲資源合理利用</li>
  <li><strong>錄像空間管理</strong>：提供錄像文件的存儲空間管理，支持自動清理與歸檔，實現存儲資源的智能管理</li>
  <li><strong>抓拍圖片管理</strong>：支持抓拍圖片的查看、檢索、下載、刪除等全生命周期管理，提供便捷的圖片管理功能</li>
  <li><strong>設備目錄管理</strong>：提供設備樹形目錄管理，支持設備分組、層級管理與權限控制，實現設備的有序組織與精細化管理</li>
  <li><strong>告警錄像</strong>：支持告警事件自動觸發錄像功能，當檢測到異常事件時自動錄制相關視頻片段，提供完整的告警證據鏈，支持告警錄像的查看、下載和管理</li>
  <li><strong>告警事件</strong>：提供完整的告警事件管理功能，支持告警事件的實時推送、歷史查詢、統計分析、事件處理與狀態跟蹤，實現告警全生命周期管理</li>
  <li><strong>錄像回放</strong>：支持歷史錄像的快速檢索與回放功能，提供時間軸定位、倍速播放、關鍵幀跳轉等便捷操作，支持多路視頻同步回放，滿足事件回溯與分析需求</li>
</ul>

#### 🌐 IoT能力

<ul style="font-size: 14px; line-height: 1.8; color: #444; margin: 10px 0;">
  <li><strong>設備接入與管理</strong>：設備註冊、認證、狀態監控、生命周期管理</li>
  <li><strong>產品與物模型管理</strong>：產品定義、物模型配置、產品管理</li>
  <li><strong>多協議支持</strong>：MQTT、TCP、HTTP等多種物聯網協議</li>
  <li><strong>設備認證與動態註冊</strong>：安全接入、身份認證、動態設備註冊</li>
  <li><strong>規則引擎</strong>：數據流轉規則、消息路由、數據轉換</li>
  <li><strong>數據采集與存儲</strong>：設備數據采集、存儲、查詢與分析</li>
  <li><strong>設備狀態監控與告警管理</strong>：實時監控、異常告警、智能決策</li>
  <li><strong>通知管理</strong>：支持7種通知方式，包括飛書、釘釘、企業微信、郵件、騰訊雲短信、阿里雲短信、Webhook，實現靈活的多渠道告警通知</li>
</ul>

#### 📱 移動端APP

<ul style="font-size: 14px; line-height: 1.8; color: #444; margin: 10px 0;">
  <li><strong>跨端覆蓋</strong>：手機、小程序與 App 多端可用，運維與管理不必綁在工位前，現場也能即時查看與處置</li>
  <li><strong>能力對齊</strong>：移動端與 PC 管控台業務能力一致，換端不換功，管控體驗無縫銜接</li>
  <li><strong>設備管理</strong>：多種接入方式統一納管，列表與通道一目了然，點開即可實時看圖，外出巡檢同樣心中有數</li>
  <li><strong>推流轉發</strong>：隨時創建與啟停轉發任務，掌握集群節點與各路流狀態，遠程也能調度視頻資源</li>
  <li><strong>算法任務</strong>：實時與抓拍算法任務隨手啟停，檢測成效隨時掌握，異常發現不必等回辦公室</li>
  <li><strong>告警中心</strong>：告警隨手檢索，抓拍與錄像即點即看，移動值守也能快速核實與跟進</li>
  <li><strong>模型管理</strong>：模型上線狀態一眼可查，部署進展心中有數</li>
  <li><strong>模型推理</strong>：現場傳圖即得識別結果，臨時核驗與抽檢不必回 PC</li>
  <li><strong>模型訓練</strong>：訓練進度隨時盯，必要時遠程一鍵叫停，避免無效算力空轉</li>
  <li><strong>個人中心</strong>：賬號、租戶與應用偏好集中管理，多端使用各得其便</li>
  <li><strong>流暢觀看</strong>：實時畫面與告警錄像在移動端流暢回放，低延時、不卡頓，移動值守體驗不打折</li>
  <li><strong>持續在線</strong>：登錄狀態自動保持，少被打斷、少重複登錄，讓「雲邊端智能管控」真正觸達手機與小程序</li>
</ul>

### 📦 內置 AI 模型

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
平台開箱即用，內置多種面向安防監控、工業現場、智慧交通等場景的預訓練模型，可在算法任務中直接選用，快速完成部署與推理，無需從零訓練即可覆蓋常見視覺檢測需求。
</p>

| 模型名稱 | 推理格式 | 基礎模型 | 能力說明 |
| :-- | :--: | :--: | :-- |
| 安全帽模型 | ONNX | YOLOv8 | 檢測作業人員是否佩戴安全帽 |
| 睡崗模型 | PyTorch | YOLOv8 | 識別崗位人員睡崗、脫崗等異常行為 |
| 人模型 | PyTorch | YOLOv8 | 通用人體檢測，用於畫面中人員的識別與定位 |
| 車牌模型 | ONNX | YOLOv8 | 識別車輛號牌信息 |
| 反光衣模型 | PyTorch | YOLOv8 | 檢測作業人員是否穿著反光衣 |
| 火焰模型 | PyTorch | YOLOv8 | 識別明火、火焰等火災隱患 |
| 吸煙模型 | PyTorch | YOLOv8 | 識別人員吸煙行為 |
| 打電話模型 | ONNX | YOLOv8 | 識別人員打電話、使用手機等行為 |
| 道路積水模型 | ONNX | YOLOv8 | 識別道路積水、路面積水等異常狀況 |
| 口罩模型 | ONNX | YOLOv8 | 檢測人員是否正確佩戴口罩 |
| 跌倒檢測模型 | ONNX | YOLOv8 | 識別人員跌倒等異常姿態 |
| 人臉檢測模型 | ONNX | YOLOv8 | 檢測畫面中人臉位置，支撐人臉識別鏈路 |

### 💡 技術理念

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
我們認為沒有任何一個編程語言能夠擅長所有事情，但通過三種編程語言的深度融合，EasyAIoT將發揮各自優勢，構建強大的技術生態。
</p>

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
Java擅長構建穩定可靠的平臺架構，但不適合網絡編程和AI編程；Python擅長網絡編程和AI算法開發，但在高性能任務執行方面存在瓶頸；C++擅長高性能任務執行，但在平臺開發和AI編程方面不如前兩者。EasyAIoT采用三合一語言混編架構，充分發揮各語言優勢，構建一個實現頗具挑戰，但使用極其便捷的AIoT平臺。
</p>

![EasyAIoT平臺架構.jpg](.image/iframe2.jpg)

### 🔄 模塊數據流轉

<img src=".image/iframe3.jpg" alt="EasyAIoT平臺架構" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">

### 🤖 零樣本標註技術

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
創新性地依托大模型構建零樣本標註技術體系（理想狀態下完全去除人工標註環節，實現標註流程的自動化），該技術通過大模型生成初始數據並借助提示詞技術完成自動標註，再經人機協同校驗確保數據質量（可選），進而訓練出初始小模型。該小模型通過持續叠代、自我優化，實現標註效率與模型精度的協同進化，最終推動系統性能不斷攀升。
</p>

<img src=".image/iframe4.jpg" alt="EasyAIoT平臺架構" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">

### 🏗️ 項目架構特點

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
EasyAIoT其實不是一個項目，而是七個項目。
</p>

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
好處是什麽呢？假如說你在一個受限的設備上（比如RK3588），你只需要拿出其中某個項目就可以獨立部署，所以看似這個項目是雲平臺，其實他也可以是邊緣平臺。
</p>

<div style="margin: 30px 0; padding: 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 10px; color: white;">

<p style="font-size: 16px; line-height: 1.8; margin: 0; font-weight: 500;">
🌟 真開源不易，如果這個項目對您有幫助，請您點亮一顆Star再離開，這將是對我最大的支持！<br>
<small style="font-size: 14px; opacity: 0.9;">（在這個假開源橫行的時代，這個項目就是一個異類，純靠愛來發電）</small>
</p>

</div>

### 🌍 本土化支持

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
EasyAIoT積極響應本土化戰略，全面支持本土化硬件和操作系統，為用戶提供安全可控的AIoT解決方案：
</p>

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; margin: 20px 0;">

<div style="padding: 20px; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🖥️ 服務器端支持</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>完美兼容海光（Hygon）x86架構處理器</li>
  <li>支持本土化服務器硬件平臺</li>
  <li>提供針對性的性能優化方案</li>
  <li>確保企業級應用的穩定運行</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">📱 邊緣端支持</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>全面支持瑞芯微（Rockchip）ARM架構芯片</li>
  <li>完美適配RK3588等主流邊緣計算平臺</li>
  <li>針對邊緣場景進行深度優化</li>
  <li>實現邊緣智能的輕量化部署</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🖱️ 操作系統支持</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>兼容麒麟（Kylin）操作系統</li>
  <li>支持方德（Founder）等本土化Linux發行版</li>
  <li>適配統信UOS等主流本土化操作系統</li>
  <li>提供完整的本土化部署方案</li>
</ul>
</div>

</div>

## 🎯 適用場景

<img src=".image/適用場景.png" alt="適用場景" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">

## 🧩 項目結構

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
EasyAIoT由七個核心項目組成：
</p>

<table style="width: 100%; border-collapse: collapse; margin: 20px 0; font-size: 14px;">
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; background-color: #f8f9fa; font-weight: 600; color: #2c3e50; width: 20%;">模塊</td>
<td style="padding: 15px; border: 1px solid #e0e0e0; background-color: #f8f9fa; font-weight: 600; color: #2c3e50;">描述</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>WEB模塊</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">基於Vue的前端管理界面，提供統一的用戶交互體驗</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>APP模塊</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">
  <ul style="margin: 5px 0; padding-left: 20px;">
    <li><strong>跨端覆蓋</strong>：一套建設、多端觸達，手機、小程序與 App 均可使用</li>
    <li><strong>能力對齊</strong>：與 PC 管控台業務能力一致，支持多租戶切換</li>
    <li><strong>設備管理</strong>：直連攝像頭、GB28181、NVR 等多協議統一納管，在線狀態與通道瀏覽，設備詳情內一鍵實時預覽</li>
    <li><strong>推流轉發</strong>：推流任務創建、啟停、集群節點狀態與多路流地址查看</li>
    <li><strong>算法任務</strong>：實時/抓拍算法任務列表、啟停控制與檢測/幀數統計</li>
    <li><strong>告警中心</strong>：告警事件檢索、抓拍圖預覽、告警錄像點播回放</li>
    <li><strong>模型與 AI</strong>：模型列表與部署狀態、移動端圖片推理工作台、訓練任務進度監控與停止</li>
    <li><strong>個人中心</strong>：個人資料、賬號安全、常見問題、意見反饋與應用設置</li>
  </ul>
</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>DEVICE模塊</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">
  <ul style="margin: 5px 0; padding-left: 20px;">
    <li><strong>技術優勢</strong>：基於JDK21，提供更好的性能和現代化特性</li>
    <li><strong>設備管理</strong>：設備註冊、認證、狀態監控、生命周期管理</li>
    <li><strong>產品管理</strong>：產品定義、物模型管理、產品配置</li>
    <li><strong>協議支持</strong>：MQTT、TCP、HTTP等多種物聯網協議</li>
    <li><strong>設備認證</strong>：設備動態註冊、身份認證、安全接入</li>
    <li><strong>規則引擎</strong>：數據流轉規則、消息路由、數據轉換</li>
    <li><strong>數據采集</strong>：設備數據采集、存儲、查詢與分析</li>
    <li><strong>節點控制面</strong>：內置 <code>iot-node</code> 微服務，提供計算/媒體節點 CRUD、SSH 連通測試、Agent 註冊與心跳、工作負載調度與媒體節點池分配等統一控制面能力</li>
  </ul>
</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>NODE模塊</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">
  <ul style="margin: 5px 0; padding-left: 20px;">
    <li><strong>節點代理</strong>：基於 Python 的邊緣/遠程節點 Agent，通過 <code>install.sh</code> 一鍵安裝為 systemd 服務，部署於目標服務器後自動接入平臺</li>
    <li><strong>控制面通信</strong>：向 <code>iot-node</code> 控制面註冊並周期性心跳，實時上報 CPU、內存、磁盤、GPU 利用率及在運工作負載狀態</li>
    <li><strong>遠程工作負載</strong>：通過 HTTP 接口（默認 9100 端口）接收控制面下發的部署/停止指令，在節點本地拉起 AI 模型服務、算法任務、FFmpeg 轉碼等工作負載</li>
    <li><strong>媒體節點池</strong>：支持在節點上遠程 <code>docker compose</code> 部署 SRS/ZLM 流媒體棧，配合控制面實現設備與媒體節點的 Sticky 綁定與流地址生成</li>
    <li><strong>節點角色</strong>：支持 compute（算力）、media（媒體）、hybrid（混合）三種角色，支撐 AI 推理、算法任務與流媒體業務的跨節點調度與彈性擴容</li>
    <li><strong>離線友好</strong>：提供 pip wheels 離線依賴打包與 Agent 熱更新能力，適配無外網或受限網絡環境下的批量節點納管</li>
  </ul>
</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>VIDEO模塊</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">
  <ul style="margin: 5px 0; padding-left: 20px;">
    <li><strong>流媒體處理</strong>：支持RTSP/RTMP流實時處理與傳輸</li>
    <li><strong>算法任務管理</strong>：支持實時算法任務和抓拍算法任務兩種類型，分別用於實時畫面分析和抓拍圖像分析</li>
    <li><strong>抽幀器與排序器</strong>：支持靈活的抽幀策略與結果排序機制，每個算法任務可綁定獨立的抽幀器和排序器</li>
    <li><strong>布防時段</strong>：支持全防模式和半防模式的時段化配置</li>
  </ul>
</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>AI模塊</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">
  <ul style="margin: 5px 0; padding-left: 20px;">
    <li><strong>智能分析</strong>：負責視頻分析和AI算法執行</li>
    <li><strong>模型服務集群</strong>：支持分佈式模型推理服務，實現負載均衡與高可用</li>
    <li><strong>實時推理</strong>：提供毫秒級響應的實時智能分析能力</li>
    <li><strong>模型管理</strong>：支持模型部署、版本管理與多實例調度</li>
  </ul>
</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; vertical-align: top;"><strong>TASK模塊</strong></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; line-height: 1.8; color: #444;">基於C++的高性能任務處理模塊，負責計算密集型任務執行</td>
</tr>
</table>

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
如需深入了解各模組技術棧、微服務拆分、中間件拓撲與資料流轉細節，請參閱 <a href=".doc/架构设计/项目架构设计分析_zh_tw.md" style="color: #3498db; text-decoration: none; font-weight: 600;">專案架構設計分析</a>。
</p>

## 🖥️ 跨平臺部署優勢

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
EasyAIoT支持在Linux、Mac、Windows三大主流操作系統上部署，為不同環境下的用戶提供靈活便捷的部署方案：
</p>

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; margin: 20px 0;">

<div style="padding: 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🐧 Linux部署優勢</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>適合生產環境，穩定可靠，資源占用低</li>
  <li>支持Docker容器化部署，一鍵啟動所有服務</li>
  <li>完美適配服務器、邊緣計算設備（如RK3588等ARM架構設備）</li>
  <li>提供完整的自動化安裝腳本，簡化部署流程</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🍎 Mac部署優勢</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>適合開發測試環境，與macOS系統深度集成</li>
  <li>支持本地開發和調試，快速驗證功能</li>
  <li>提供便捷的安裝腳本，支持Homebrew等包管理器</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🪟 Windows部署優勢</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>適合Windows服務器環境，降低學習成本</li>
  <li>支持PowerShell自動化腳本，簡化部署操作</li>
  <li>兼容Windows Server和桌面版Windows系統</li>
  <li>提供圖形化安裝向導，用戶友好</li>
</ul>
</div>

</div>


<p style="font-size: 14px; line-height: 1.8; color: #2c3e50; font-weight: 500; margin: 20px 0; padding: 15px; background-color: #e8f4f8; border-left: 4px solid #3498db; border-radius: 4px;">
<strong>統一體驗</strong>：無論選擇哪種操作系統，EasyAIoT都提供一致的安裝腳本和部署文檔，確保跨平臺部署體驗的一致性。
</p>

## ☁️ EasyAIoT = AI + IoT = 雲邊端一體化解決方案

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
支持上千種垂直場景，支持AI模型定制化和AI算法定制化開發，深度融合。
</p>

<div style="background-color: #f8f9fa; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #3498db;">
<h3 style="color: #2c3e50; margin-top: 0;">賦能萬物智視：EasyAIoT</h3>
<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 10px 0;">
構築了物聯網設備（尤其是海量攝像頭）的高效接入與管控網絡。我們深度融合流媒體實時傳輸技術與前沿人工智能（AI），打造一體化服務核心。這套方案不僅打通了異構設備的互聯互通，更將高清視頻流與強大的AI解析引擎深度集成，賦予監控系統"智能之眼"——精準實現人臉識別、異常行為分析、風險人員布控及周界入侵檢測。
</p>
<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 10px 0;">
平臺支持兩種類型的算法任務：實時算法任務用於RTSP/RTMP流的實時畫面分析，提供毫秒級響應能力；抓拍算法任務用於抓拍圖像的智能分析，支持事件回溯與圖像檢索。通過算法任務管理實現靈活的抽幀與排序策略，每個任務可綁定獨立的抽幀器和排序器，結合模型服務集群推理能力，確保毫秒級響應與高可用保障。同時，提供全防模式和半防模式兩種布防策略，可根據不同時段靈活配置監控規則，實現精準的時段化智能監控與告警。
</p>
<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 10px 0;">
在物聯網設備管理方面，EasyAIoT提供完整的設備生命周期管理能力，支持多種物聯網協議（MQTT、TCP、HTTP），實現設備的快速接入、安全認證、實時監控和智能控制。通過規則引擎實現設備數據的智能流轉與處理，結合AI能力對設備數據進行深度分析，實現從設備接入、數據采集、智能分析到決策執行的全流程自動化，真正實現萬物互聯、萬物智控。
</p>
</div>

<img src=".image/iframe1.jpg" alt="EasyAIoT平臺架構" style="max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15);">

## ⚠️ 免責聲明

EasyAIoT是一個開源學習項目，與商業行為無關。用戶在使用該項目時，應遵循法律法規，不得進行非法活動。如果EasyAIoT發現用戶有違法行為，將會配合相關機關進行調查並向政府部門舉報。用戶因非法行為造成的任何法律責任均由用戶自行承擔，如因用戶使用造成第三方損害的，用戶應當依法予以賠償。使用EasyAIoT所有相關資源均由用戶自行承擔風險.

## 📚 部署文檔

- [平臺部署文檔](.doc/部署文档/平台部署文档_zh_tw.md) — Linux / Mac / Windows 分步部署指南
- [部署最佳實踐](.doc/部署文档/部署最佳实践_zh_tw.md) — 環境要求、一鍵部署流程、維運排錯與生產環境建議

## 🎮 演示環境

- 演示地址：http://36.111.47.113:8888/
- 賬號：admin
- 密碼：admin123

## ⚙️ 項目地址

- Gitee: https://gitee.com/soaring-xiongkulu/easyaiot
- Github: https://github.com/soaring-xiongkulu/easyaiot

## 📸 截圖
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

## 🛠️ 服務支持

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
我們提供了各種服務方式幫助您深入了解EasyAIoT平臺和代碼，通過產品文檔、技術交流群、付費教學等方式，你將獲得如下服務：
</p>

<table style="width: 100%; table-layout: fixed; border-collapse: collapse; margin: 20px 0; font-size: 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
<thead>
<tr style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white;">
<th style="padding: 15px; text-align: left; font-weight: 600;">服務項</th>
<th style="padding: 15px; text-align: left; font-weight: 600;">服務內容</th>
<th style="padding: 15px; text-align: center; font-weight: 600;">服務收費</th>
<th style="padding: 15px; text-align: left; font-weight: 600;">服務方式</th>
</tr>
</thead>
<tbody>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50;">系統部署</td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444;">在客戶指定的網絡和硬件環境中完成EasyAIoT部署</td>
<td style="padding: 15px; border: 1px solid #e0e0e0; text-align: center; color: #e74c3c; font-weight: 600;">1000元</td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444;">線上部署支持</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50;">技術支持</td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444;">提供各類部署、功能使用中遇到的問題答疑</td>
<td style="padding: 15px; border: 1px solid #e0e0e0; text-align: center; color: #e74c3c; font-weight: 600;">500元</td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444;">半小時內 線上遠程支持</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50;">其他服務</td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444;">垂直領域解決方案定制化開發；定制化時長、功能服務等</td>
<td style="padding: 15px; border: 1px solid #e0e0e0; text-align: center; color: #e74c3c; font-weight: 600;">面議</td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444;">面議</td>
</tr>
</tbody>
</table>

## 📞 聯系方式

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
請先關注下方公眾號，再通過技術交流群或微信號與我們聯系。
</p>

## 👥 公眾號

<div>
  <img src=".image/公众号.jpg" alt="公眾號" width="30%">
</div>

## 💬 技術交流群

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
關注公眾號後，使用微信掃描下方二維碼加入 EasyAIoT 技術交流群。
</p>

<div>
  <img src=".image/交流群3群.jpg" alt="EasyAIoT技術交流3群" width="30%">
</div>

## 💬 微信號聯系

<p style="font-size: 14px; line-height: 1.8; color: #555; margin: 15px 0;">
關注公眾號後，如需一對一溝通，可掃描下方二維碼添加微信好友。
</p>

<div>
  <img src=".image/微信联系方式.jpg" alt="微信號聯系方式" width="200">
</div>

## 🪐 知識星球：

<p>
  <img src=".image/知識星球.jpg" alt="知識星球" width="30%">
</p>

## 💰 打賞贊助

<div>
    <img src=".image/微信支付.jpg" alt="微信支付" width="30%" height="30%">
    <img src=".image/支付寶支付.jpg" alt="支付寶支付" width="30%" height="10%">
</div>

## 🤝 貢獻指南

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
我們歡迎所有形式的貢獻！無論您是代碼開發者、文檔編寫者，還是問題反饋者，您的貢獻都將幫助 EasyAIoT 變得更好。以下是幾種主要的貢獻方式：
</p>

<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; margin: 20px 0;">

<div style="padding: 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">💻 代碼貢獻</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>Fork 項目到您的 GitHub/Gitee 賬號</li>
  <li>創建特性分支 (git checkout -b feature/AmazingFeature)</li>
  <li>提交更改 (git commit -m 'Add some AmazingFeature')</li>
  <li>推送到分支 (git push origin feature/AmazingFeature)</li>
  <li>提交 Pull Request</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">📚 文檔貢獻</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>完善現有文檔內容</li>
  <li>補充使用示例和最佳實踐</li>
  <li>提供多語言翻譯</li>
  <li>修正文檔錯誤</li>
</ul>
</div>

<div style="padding: 20px; background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); border-radius: 10px; color: white; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
<h4 style="margin-top: 0; color: white; font-size: 18px;">🌟 其他貢獻方式</h4>
<ul style="font-size: 14px; line-height: 1.8; margin: 10px 0; padding-left: 20px;">
  <li>報告並修復 Bug</li>
  <li>提出功能改進建議</li>
  <li>參與社區討論，幫助其他開發者</li>
  <li>分享使用經驗和案例</li>
</ul>
</div>

</div>

## 🌟 重大貢獻者

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
以下是對EasyAIoT項目做出重大貢獻的傑出貢獻者，他們的貢獻對項目的發展起到了關鍵推動作用，我們表示最誠摯的感謝！
</p>

<table style="width: 100%; table-layout: fixed; border-collapse: collapse; margin: 20px 0; font-size: 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
<thead>
<tr style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white;">
<th style="padding: 15px; text-align: left; font-weight: 600; border: 1px solid #e0e0e0; width: 32%; min-width: 9rem;">貢獻者</th>
<th style="padding: 15px; text-align: left; font-weight: 600; border: 1px solid #e0e0e0;">貢獻內容</th>
</tr>
</thead>
<tbody>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>℡夏別</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">為推動EasyAIoT項目貢獻Windows部署文檔，為Windows平台用戶提供了完整的部署指南，大大降低了Windows環境下的部署難度，讓更多用戶能夠便捷地使用EasyAIoT平台。</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>YiYaYiYaho</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">為推動EasyAIoT項目貢獻Mac容器一鍵部署腳本，為Mac平台用戶提供了自動化部署解決方案，顯著簡化了Mac環境下的部署流程，提升了開發者和用戶的部署體驗。</td>
</tr>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>山寒</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">為推動EasyAIoT項目貢獻Linux容器部署腳本，為Linux平台用戶提供了容器化部署方案，實現了快速、可靠的容器部署，為生產環境的穩定運行提供了重要保障。</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>玖零。</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">為推動EasyAIoT項目貢獻Linux容器部署腳本，進一步完善了Linux平台的容器化部署方案，為不同Linux發行版用戶提供了更多選擇，推動了項目的跨平台部署能力。</td>
</tr>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>爱吃小柚子</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">為推動EasyAIoT項目在視訊監控與智能分析方向的發展，主導並完成國標 GB28181 與 AI 業務流程的端到端聯調與驗證測試；同時承擔畫面清晰度與播放流暢度的專項測試與評估，為國標接入可靠性、鏈路穩定性以及視訊觀感體驗的持續優化提供了重要依據。</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>Dark</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">為推動EasyAIoT項目在國標視訊監控方向的發展，貢獻 GB28181 能力的端到端打通，實現視訊播放與雲台控制，使國標設備接入具備可用的實況預覽與遠端操控能力。</td>
</tr>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>machh</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">為推動 EasyAIoT-Edge 項目的發展，完成攝影機接入與 AI 能力的端到端跑通，並實現功能串聯，使邊緣側「接入—智能分析」鏈路可用、可閉環。</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>遗忘的星空</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">為推動 EasyAIoT 項目在設備直連接入方向的發展，貢獻多品牌 IP 攝影機資產盤點與網段掃描能力，支援海康 IPC、NVR 等設備的批量發現與識別；完善直連設備於同網段、跨網段場景下的批量搜尋與一鍵註冊流程，基於設備原生協議實現接入，可繞過海康 SDK、擺脫對海康平台的強依賴，為開放、可控的攝影機規模化接入奠定了基礎。</td>
</tr>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>阿龙</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">為推動 EasyAIoT 項目在地圖視覺化與空間研判方向的發展，獨立貢獻天地圖空間視覺化能力的完整程式碼實作，涵蓋國家天地圖底圖接入、攝影機與告警設備布點、地圖分布視圖、地點搜尋與座標批量匯入、告警事件自動上圖、以人/以車尋跡及行動裝置軌跡回放等核心鏈路，使平台「天地圖空間視覺化與以圖研判」能力從方案設計真正走向可落地、可使用的生產形態。</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>雨落流殇</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">為推動 EasyAIoT 項目在超大規模串流媒體承載方向的發展，貢獻 SRS 與 ZLMediaKit 異構串流媒體伺服器叢集的部署架構與排程思路，提出多節點池協同、串流媒體控制面與業務層解耦、儲存與上傳流水線及節點註冊排程等可擴展方案，為平台支撐萬級路攝影機並發接入、穩定分發與彈性擴容奠定了重要的架構基礎。</td>
</tr>
<tr style="background-color: #f8f9fa;">
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>常康</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">為推動 EasyAIoT 項目在智慧交通與車輛管控方向的發展，獨立貢獻車牌識別演算法與完整程式碼實作，涵蓋基於 ONNX 的車牌定位檢測、號碼與顏色識別、雙層牌拼接與傾斜透視校正、車牌庫管理與多庫順序匹配、演算法任務一鍵聯動及 Kafka 非同步比對等核心鏈路，全面支援藍/黃/綠/白牌及新能源車牌等主流類型，使平台「車牌識別與車牌庫管理能力」從能力規劃真正走向可落地、可閉環的生產應用。</td>
</tr>
<tr>
<td style="padding: 15px; border: 1px solid #e0e0e0; font-weight: 600; color: #2c3e50; width: 32%; min-width: 9rem;"><nobr>Li</nobr></td>
<td style="padding: 15px; border: 1px solid #e0e0e0; color: #444; line-height: 1.8;">為推動 EasyAIoT 項目在青年開發者社群與協作生態方向的發展，以卓越的組織領導力與感召力，帶領全校同學深度參與項目共建，匯聚青春才智、凝聚團隊合力，為 EasyAIoT 注入了源源不絕、綿延不絕的發展動能；在項目傳播推廣、實踐落地與後續人才梯隊培育等方面，亦作出了舉足輕重、不可替代的重要貢獻。</td>
</tr>
</tbody>
</table>

<p style="font-size: 14px; line-height: 1.8; color: #2c3e50; font-weight: 500; margin: 20px 0; padding: 15px; background-color: #e8f4f8; border-left: 4px solid #3498db; border-radius: 4px;">
<strong>特別致謝</strong>：以上貢獻者在跨平台部署文檔與腳本、國標視訊能力落地與 AI 聯調驗證、多品牌攝影機直連發現與批量接入、天地圖空間視覺化完整落地、異構串流媒體叢集部署與排程架構、車牌識別演算法與完整程式碼落地、EasyAIoT-Edge 邊緣側端到端串聯、校園開發者社群組織與青年協作生態構建等不同方面推動了 EasyAIoT 的發展，他們的專業精神與無私奉獻值得我們學習與尊敬。再次向這些傑出的貢獻者表示最誠摯的感謝！🙏
</p>

## 💝 開源守望者

開源專案的持續推進，從來不只依賴程式碼與文件。在 EasyAIoT 算力資源最吃緊、專案幾近難以為繼的那些日子裡，正是以下各位以真金白銀的支持，為專案注入了最關鍵的續航——你們或許未曾提交一行程式碼，但每一份信任與托舉，都讓這個專案得以跨過最難的檻、繼續向前迭代。只要有人在用、有人在撐，開源生態便值得走得更遠；EasyAIoT 今日所能抵達的高度，離不開這些在關鍵時刻雪中送炭的同行者。我們向每一位給予援手的朋友致以最誠摯的敬意與感謝！以下排名不分先後：

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

## 🏆 最佳實踐者

他們是將 EasyAIoT 從「可用」推向「好用、用好」的先行者——以下各位已完成 EasyAIoT 專案部署或業務場景落地，其探索與成果為社群樹立了可複製、可參考的標竿，我們向這些卓越實踐者致以崇高敬意與衷心祝賀！以下排名不分先後：

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

## 🙏 致謝

感謝以下各位對本項目包括但不限於代碼貢獻、問題反饋、資金捐贈等各種方式的支持！以下排名不分先後：
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
歡迎提出更好的意見，幫助完善 easyaiot
</p>

## 📄 版權

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
翺翔的雄庫魯/easyaiot 采用 <a href="https://gitee.com/soaring-xiongkulu/easyaiot/blob/main/LICENSE" style="color: #3498db; text-decoration: none; font-weight: 600;">MIT LICENSE</a> 開源協議。我們致力於推動 AI 技術的普及與發展，讓更多人能夠自由使用和受益於這項技術。
</p>

<p style="font-size: 15px; line-height: 1.8; color: #333; margin: 15px 0;">
<strong>使用許可</strong>：個人與企業可 100% 免費使用，無需保留作者、Copyright 資訊。我們相信技術的價值在於被廣泛使用和持續創新，而非被版權束縛。希望您能夠自由地使用、修改、分發本項目，讓 AI 技術真正惠及每一個人。
</p>

## 🌟 Star增長趨勢圖

[![Stargazers over time](https://starchart.cc/soaring-xiongkulu/easyaiot.svg?variant=adaptive)](https://starchart.cc/soaring-xiongkulu/easyaiot)