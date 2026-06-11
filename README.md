# SDInteractive

> 基于 ASR 分段理解与 Manifest 调度的短剧即时互动激发系统

SDInteractive 是一个面向短剧观看场景的 Android 即时互动 App。项目围绕“短剧播放、剧情高光打标、互动 Manifest 下发、端上时间轴调度、用户行为沉淀、AI 辅助打标、当前帧人物识别”构建完整闭环。

用户可以在 Android App 中播放本地托管的短剧视频，并在剧情高光、反转、爽点、知识点、人物出场和剧尾悬念等节点看到轻量互动组件，例如情绪按钮、剧情竞猜、高能预警、知识卡片、人物识别、人物关系图谱、演技评分和名场面收藏等。

项目既可以作为“边看短剧边互动”的体验型 App，也可以作为一个 AI 全栈工程样例，展示移动端播放、后端视频分发、互动调度、AI 候选生成、模型后处理和降级策略如何协同工作。

---

## 目录

* [项目定位](#项目定位)
* [核心能力](#核心能力)
* [算法与 AI 设计](#算法与-ai-设计)
* [系统架构](#系统架构)
* [技术选型](#技术选型)
* [项目结构](#项目结构)
* [快速运行](#快速运行)
* [APK 体验](#apk-体验)
* [AI 配置](#ai-配置)
* [接口概览](#接口概览)
* [测试与构建](#测试与构建)
* [公开发布安全说明](#公开发布安全说明)
* [后续规划](#后续规划)

---

## 项目定位

短剧内容具有强情绪、高反转、快节奏和人物关系密集等特点。传统弹幕和评论需要用户输入文字，表达门槛高，并且容易打断观看节奏。SDInteractive 尝试用更轻量的方式激发用户即时互动：在剧情关键节点自动展示语义化互动组件，让用户通过点击、选择、收藏、评分和识别人物等方式完成情绪表达和剧情参与。

本项目当前以 Android App + Local Server 的形式交付：

* Android App 负责短剧播放、互动渲染、用户操作和本地状态管理。
* Local Server 负责视频托管、剧集目录、互动 Manifest、AI 接口、人物识别和用户行为记录。
* AI 模型负责辅助生成互动候选点位和识别当前帧人物。
* 规则化后处理负责约束模型输出，保证端上体验稳定、可控、可复现。

这种架构具有三个优势：

1. **视频不打入 APK**
   短剧 MP4 由服务端托管，降低安装包体积，也便于替换素材和扩展部署。

2. **互动逻辑 Manifest 化**
   互动点位、触发方式、展示时长、优先级和 payload 由服务端统一下发，客户端不需要硬编码所有剧情逻辑。

3. **AI 能力可控接入**
   大模型只作为候选生成器和理解辅助，最终进入端上的内容需要经过白名单、置信度、时间窗口密度和 fallback 规则约束。

---

## 核心能力

### 1. 短剧播放基础能力

* 支持短剧列表和剧集列表展示。
* 支持 Android 竖屏短视频式播放体验。
* 基于 Media3 ExoPlayer 播放后端托管 MP4。
* 支持播放、暂停、进度条拖动、切集、续播和相邻剧集预加载。
* 服务端支持 HTTP Range，满足移动端拖动进度和渐进式播放需求。
* 视频资源放在本地 `video/` 目录，不随公开仓库发布。

### 2. 剧情高光互动

项目内置多种剧情互动组件，覆盖短剧观看中的常见情绪和参与场景：

* 情绪按钮：如“爽”“忍”“公子大气”等。
* 数值助推：如怒气值、爽感值、热度值等。
* 剧情竞猜：在关键剧情节点让用户预测后续走向。
* 竞猜结果：展示用户选择和群体倾向。
* 高能预警：在冲突、危险、反转前短时提示。
* 知识卡片：解释剧情中的文化背景、梗点、人物身份或设定信息。
* 演技评分：对高光表演片段进行轻量评分。
* 名场面收藏：保存高光片段到本地收藏列表。
* 人物识别入口：在关键人物出现时提供识别能力。
* 人物卡片：展示人物身份、标签、剧情角色和当前看点。
* 人物关系图谱：随剧情推进逐步展示人物关系。
* 全站播报：展示轻量氛围反馈。

### 3. 时间轴调度机制

互动事件支持三类触发器：

| 触发器             | 说明       | 适用场景            |
| --------------- | -------- | --------------- |
| `Fixed`         | 固定时间点触发  | 爽点、反转、名场面、高能台词  |
| `Range`         | 时间窗口内可触发 | 人物识别、知识卡片、评分、收藏 |
| `EpisodeEnding` | 剧集结尾触发   | 悬念、追更、分支、竞猜     |

客户端通过 InteractionScheduler 根据播放进度、seek 状态、触发类型、优先级和用户状态决定是否展示互动组件。Fixed 点位不仅判断当前时间是否命中，还会判断播放快照是否跨过触发点，减少缓冲、采样间隔或解码跳帧导致的漏触发。

### 4. 用户行为闭环

项目支持轻量用户行为沉淀：

* 游客登录。
* 点赞、收藏、评论。
* 播放进度上报。
* 互动事件上报。
* QoE 事件上报。
* 本地保存竞猜答案、评分记录、数值状态和收藏片段。
* 服务端维护轻量会话态用户行为数据。

当前存储方案面向 MVP 和演示场景，优先保证闭环完整和部署简单。接口边界已保留，后续可以平滑迁移到 SQLite、PostgreSQL、Redis 或云数据库。

---

## 算法与 AI 设计

### 1. 算法方案取舍

短剧即时互动的核心不是对每一帧做视觉理解，而是找到“最适合激发用户情绪和参与感的剧情时刻”。这类时刻通常由台词冲突、身份揭示、人物关系变化、情绪反转、爽点爆发、文化梗点和剧尾悬念共同构成，其中大量关键信息集中在语音和台词文本中。

因此，本项目采用：

> ASR 文本分段为主，人工精选为锚点，视觉模型为辅助，规则后处理为保障。

相比直接进行全量视频理解，该方案在 MVP 阶段更适合短剧即时互动场景：

| 方案               | 优点                           | 问题                                 | 本项目取舍         |
| ---------------- | ---------------------------- | ---------------------------------- | ------------- |
| 全量视频理解           | 信息最完整                        | 成本高、延迟大、接口额度压力大，短剧镜头切换快，模型输出稳定性不可控 | 不作为 MVP 主链路   |
| 纯人工打点            | 稳定、可控、演示效果好                  | 可扩展性弱，无法体现 AI 候选生成能力               | 作为高质量基准和审核锚点  |
| ASR 分段 + 大模型     | 成本低、速度快、可解释，适合识别冲突、反转、爽点、知识点 | 对纯视觉笑点和动作戏敏感度不足                    | 作为核心候选生成方案    |
| 当前帧视觉识别          | 能补充人物身份、服化道和场景证据             | 不适合承担所有剧情打标                        | 用于人物识别和剧情辅助解释 |
| 规则后处理 + Manifest | 可控、可审核、可下发、可复现               | 需要额外设计数据结构和调度规则                    | 作为工程落地核心      |

### 2. AI 互动点位打标

服务端提供 AI 互动打标接口，可接收 ASR 文本分段，并调用 Ark / Doubao 兼容模型生成互动候选点位。

流程如下：

1. 输入带时间戳的 ASR 文本分段。
2. Prompt 中注入剧情背景、人物关系、互动风格和组件白名单。
3. 大模型生成候选互动点位。
4. 服务端进行 JSON 解析和字段归一化。
5. 通过类型白名单、置信度、时间窗口密度和 duration 限制过滤候选。
6. 输出 Manifest Preview，用于后续人工审核、固化或端上下发。

当前支持的互动类型包括：

```text
emotion
value_boost
quiz
quiz_result
rating
knowledge
warning
highlight_collect
global_broadcast
none
```

未知类型会统一降级为 `none`，避免端上出现无法渲染的组件。

### 3. 规则化降级策略

AI 能力是可选项。未配置模型或模型返回异常时，服务端会自动进入规则化降级策略，保证核心演示链路仍然可用。

示例策略：

* 包含“会不会、谁会赢、是否”等语义时，生成剧情竞猜。
* 包含“高能、危险、小心”等语义时，生成高能预警。
* 包含“古代、官职、怡香院”等背景词时，生成知识卡片。
* 包含“哈哈、爽、本公子、大气、忍、封神”等情绪词时，生成情绪互动。

降级策略不是替代模型，而是保证模型不可用时项目仍具备可运行、可演示、可复现的完整闭环。

### 4. 当前帧人物识别

人物识别采用“视觉证据 + 剧情约束”的混合方案：

1. Android 端通过 PixelCopy 截取当前视频帧。
2. 客户端将截图、剧集 ID 和播放时间点发送到后端。
3. 后端结合人物目录、当前剧集时间线和候选人物范围。
4. 调用 Vision 模型识别当前帧人物。
5. 后处理阶段进行人物 ID 校验、置信度过滤和数量限制。
6. 返回人物身份、标签、剧情角色、视觉证据和当前看点。

该方案避免了高频逐帧识别带来的成本和延迟，也降低了短剧服化道相似、镜头切换快导致的模型幻觉风险。

---

## 系统架构

```text
Android App
  ├─ Jetpack Compose UI
  ├─ Media3 ExoPlayer
  ├─ Interaction Scheduler / Coordinator
  ├─ Local Interaction Storage
  ├─ PixelCopy Frame Capture
  └─ Retrofit + OkHttp
          │
          │ HTTP over LAN
          ▼
Local Server (Ktor / Netty)
  ├─ Drama / Episode / Play APIs
  ├─ MP4 Static Hosting with Range Support
  ├─ Interaction Manifest APIs
  ├─ User Action and Profile APIs
  ├─ Playback / QoE / Interaction Event APIs
  ├─ AI Interaction Tagging
  ├─ AI Person Identification
  └─ Rule-based Fallback
          │
          │ Optional
          ▼
Ark-compatible Chat Completions / Vision Model
```

整体链路：

```text
本地 MP4 视频
  → Ktor 扫描生成剧集目录
  → Android 请求剧集列表和播放地址
  → Media3 播放远程 MP4
  → 客户端请求 Interaction Manifest
  → InteractionScheduler 按播放时间触发互动
  → 用户点击互动组件
  → 本地状态保存 + 服务端行为上报
  → ASR 分段可进入 AI 打标接口生成新候选
  → 当前帧截图可进入人物识别接口生成角色看点
```

---

## 技术选型

| 模块          | 技术                                       | 选择原因                                     |
| ----------- | ---------------------------------------- | ---------------------------------------- |
| Android 客户端 | Kotlin / Android 原生                      | 更贴近移动短剧真实消费场景，便于调用原生播放、截图和生命周期能力         |
| UI 框架       | Jetpack Compose / Material3              | 状态驱动、组合灵活，适合构建高频变化的互动浮层、动画反馈和模态图谱        |
| 视频播放        | AndroidX Media3 ExoPlayer                | 支持远程 MP4、缓冲、seek、进度采样和播放生命周期管理           |
| 网络访问        | Retrofit / OkHttp                        | Android 端成熟稳定，便于统一处理服务地址、超时、请求模型和错误状态    |
| 后端服务        | Kotlin / Ktor / Netty                    | 轻量、启动快，适合 Local Server、REST API 和局域网演示   |
| 序列化         | kotlinx.serialization / Gson             | 支持强类型 JSON 建模，便于 Manifest、用户行为和 AI 结果归一化 |
| 视频分发        | Ktor Static Hosting + HTTP Range         | 不把视频打入 APK，便于替换素材、拖动进度和渐进式播放             |
| AI 接入       | Ark-compatible Chat Completions / Vision | 兼容文本理解和视觉识别能力，便于替换模型和统一服务端转发             |
| 数据下发        | Interaction Manifest                     | 可审核、可缓存、可复现，避免端上硬编码所有互动逻辑                |
| 本地存储        | SharedPreferences                        | 适合 MVP 阶段保存竞猜、评分、收藏和数值状态                 |
| 部署          | Gradle / Docker Compose                  | 同时覆盖本机开发、局域网演示和容器化部署                     |
| 测试          | JUnit / Ktor test host                   | 覆盖互动调度、接口行为、视频 Range、AI 后处理和人物识别         |

---

## 项目结构

```text
SDInteractive/
  android-app/                  # Android 客户端源码
  server/                       # Ktor Local Server 源码
  docs/
    DEPLOY_AND_PACKAGE.md       # 部署与 APK 打包说明
  gradle/                       # Gradle Wrapper 相关文件
  release/                      # 可选：演示 APK 输出目录
  .env.example                  # AI 配置模板，不包含真实密钥
  docker-compose.yml            # Docker Compose 配置
  Dockerfile                    # 后端镜像构建配置
  README.md                     # 项目说明文档
```

以下目录或文件仅用于本地运行和调试，不应提交到公开仓库：

```text
.env
local.properties
.gradle/
.kotlin/
build/
**/build/
video/
*.log
*.keystore
*.jks
```

---

## 快速运行

### 环境要求

* Windows 10/11、macOS 或 Linux
* JDK：按项目 Gradle 配置准备对应版本
* Android Studio
* Android SDK，项目使用的 `compileSdk` 以 Gradle 配置为准
* Android 8.0 及以上真机或模拟器
* 真机局域网访问时，手机和电脑需要处于同一 Wi-Fi

---

### 1. 准备短剧视频

公开仓库不包含原始短剧视频素材。请在项目根目录创建 `video/` 目录，并按剧集顺序放置 MP4 文件：

```text
video/
  第1集.mp4
  第2集.mp4
  第3集.mp4
  ...
```

服务端会扫描 `video/` 目录，生成 `ep_001`、`ep_002` 等剧集 ID，并为每集提供播放地址。

---

### 2. 启动 Local Server

#### Windows PowerShell

```powershell
cd <PROJECT_ROOT>
$env:VIDEO_DIR="$PWD\video"
.\gradlew.bat :server:run
```

#### macOS / Linux

```bash
cd <PROJECT_ROOT>
export VIDEO_DIR="$PWD/video"
./gradlew :server:run
```

默认服务端口为：

```text
8081
```

启动后可以检查接口：

```bash
curl http://localhost:8081/api/dramas
curl http://localhost:8081/api/dramas/drama_001/episodes
curl http://localhost:8081/api/episodes/ep_001/play
```

检查视频 Range 是否可用：

```bash
curl -I -H "Range: bytes=0-15" http://localhost:8081/static/videos/ep_001.mp4
```

如果返回 `206 Partial Content`，表示视频 Range 请求可用。

---

### 3. Android 真机局域网访问

先查看电脑在局域网内的 IP 地址。

Windows：

```powershell
ipconfig
```

macOS / Linux：

```bash
ifconfig
```

假设电脑局域网 IP 为 `<YOUR_LAN_IP>`，启动服务端前设置：

#### Windows PowerShell

```powershell
cd <PROJECT_ROOT>
$env:VIDEO_DIR="$PWD\video"
$env:PUBLIC_BASE_URL="http://<YOUR_LAN_IP>:8081"
.\gradlew.bat :server:run
```

#### macOS / Linux

```bash
cd <PROJECT_ROOT>
export VIDEO_DIR="$PWD/video"
export PUBLIC_BASE_URL="http://<YOUR_LAN_IP>:8081"
./gradlew :server:run
```

然后在 Android App 的服务地址中填写：

```text
http://<YOUR_LAN_IP>:8081
```

---

### 4. 使用 Docker Compose 启动后端

确保项目根目录存在 `video/` 目录，然后执行：

```bash
cd <PROJECT_ROOT>
docker compose up -d --build
```

如需指定对外访问地址，可在启动前设置：

```bash
export PUBLIC_BASE_URL="http://<YOUR_LAN_IP>:8081"
docker compose up -d --build
```

Windows PowerShell：

```powershell
$env:PUBLIC_BASE_URL="http://<YOUR_LAN_IP>:8081"
docker compose up -d --build
```

---

## APK 体验

如果仓库中提供了演示 APK，可在以下目录查看：

```text
release/SDInteractive-demo-release.apk
```

安装 APK 后，在 App 的设置页或“我的”页面中修改服务地址：

```text
http://<YOUR_LAN_IP>:8081
```

如果使用 Android 模拟器访问宿主机服务，可尝试填写：

```text
http://10.0.2.2:8081
```

如果使用 USB 真机调试，也可以通过 adb reverse 转发端口：

```bash
adb reverse tcp:8081 tcp:8081
```

然后在 App 中填写：

```text
http://127.0.0.1:8081
```

---

## AI 配置

AI 能力是可选项。未配置 API Key 时，服务端会自动使用规则化降级策略，核心播放、互动和演示链路仍然可运行。

如需启用模型能力，请复制 `.env.example` 为 `.env`，并仅在本地填写真实配置：

```dotenv
ARK_API_KEY=your_api_key_here
ARK_MODEL=your_model_or_endpoint_id
ARK_BASE_URL=https://your_ark_compatible_base_url
```

支持的兼容变量包括：

```text
ARK_API_KEY
DOUBAO_API_KEY
VOLCENGINE_API_KEY
APIKEY

ARK_MODEL
DOUBAO_MODEL
ARK_ENDPOINT_ID
EP
MODEL

ARK_BASE_URL
DOUBAO_BASE_URL
VOLCENGINE_BASE_URL
```

---

## 接口概览

| 方法     | 路径                                               | 说明              |
| ------ | ------------------------------------------------ | --------------- |
| `POST` | `/api/auth/guest`                                | 游客登录            |
| `GET`  | `/api/dramas`                                    | 获取短剧列表          |
| `GET`  | `/api/dramas/{dramaId}/episodes`                 | 获取剧集列表          |
| `GET`  | `/api/episodes/{episodeId}/play`                 | 获取播放地址          |
| `GET`  | `/api/episodes/{episodeId}/interaction-manifest` | 获取剧集互动 Manifest |
| `GET`  | `/static/videos/{episodeId}.mp4`                 | MP4 Range 播放    |
| `POST` | `/api/users/{userId}/actions`                    | 点赞、收藏、评论、分享     |
| `GET`  | `/api/users/{userId}/profile`                    | 用户互动资产          |
| `POST` | `/api/playback/progress`                         | 播放进度上报          |
| `POST` | `/api/qoe/events`                                | QoE 事件上报        |
| `POST` | `/api/interactions/events`                       | 互动事件上报          |
| `POST` | `/api/ai/interaction-tagging`                    | AI 互动点位打标       |
| `POST` | `/api/ai/person-identify`                        | 当前帧人物识别         |
| `POST` | `/api/ai/person-insight`                         | 人物即时看点生成        |
| `POST` | `/api/ai/branch-content`                         | 预留剧情分支生成任务      |
| `GET`  | `/api/ai/branch-content/{taskId}`                | 查询分支生成任务状态      |

---

## 测试与构建

### 运行全部测试

Windows PowerShell：

```powershell
cd <PROJECT_ROOT>
.\gradlew.bat test
```

macOS / Linux：

```bash
cd <PROJECT_ROOT>
./gradlew test
```

### 构建 Android Debug 包

Windows PowerShell：

```powershell
cd <PROJECT_ROOT>
.\gradlew.bat :android-app:assembleDebug
```

macOS / Linux：

```bash
cd <PROJECT_ROOT>
./gradlew :android-app:assembleDebug
```

输出目录通常为：

```text
android-app/build/outputs/apk/debug/
```

### 构建 Android Release 包

```bash
./gradlew :android-app:assembleRelease
```

也可以在构建时指定默认服务端地址：

```bash
./gradlew :android-app:assembleRelease -PserverBaseUrl=http://<YOUR_LAN_IP>:8081
```

Windows PowerShell：

```powershell
.\gradlew.bat :android-app:assembleRelease -PserverBaseUrl=http://<YOUR_LAN_IP>:8081
```

输出目录通常为：

```text
android-app/build/outputs/apk/release/
```

### 构建后端分发包

Windows PowerShell：

```powershell
cd <PROJECT_ROOT>
.\gradlew.bat :server:installDist
```

macOS / Linux：

```bash
cd <PROJECT_ROOT>
./gradlew :server:installDist
```

---

## 测试覆盖范围

项目已围绕以下关键路径设计测试：

* 互动调度器触发、优先级、暂停、seek、剧集切换和结尾互动。
* 内置互动事件的唯一性、合法性和触发可达性。
* 本地互动记录的编解码、去重和异常容错。
* Ktor 接口、视频目录解析、MP4 Range 请求和用户行为记录。
* AI 互动打标的 JSON 归一化、fallback 和窗口密度控制。
* 人物识别的人物目录、视觉结果过滤、时间线 fallback 和单帧证据约束。

---



### 视频素材说明

由于短剧素材通常涉及版权，公开仓库不包含原始视频文件。运行项目前，需要自行准备可合法使用的 MP4 视频，并放入本地 `video/` 目录。



---

## 后续规划

项目当前重点完成 MVP 闭环和可演示能力，后续可以继续扩展：

1. **审核后台**
   将 AI 生成的 Manifest Preview 接入人工审核后台，形成“ASR / 视频理解 → AI 预打标 → 人工审核 → 发布 → 端上下发”的完整生产链路。

2. **数据库与实时统计**
   将当前轻量会话态存储替换为 PostgreSQL / Redis，支持互动热度、用户画像、实时排行榜和多端同步。

3. **更强的多模态理解**
   在 ASR 分段基础上加入关键帧抽取、镜头质量过滤、多帧投票和视频片段理解，提高纯视觉高光点识别能力。

4. **剧情分支与 AIGC 内容**
   在剧尾或不影响主线收束的位置接入剧情分支、番外生成和图文 / 视频 AIGC 内容。

5. **互动策略个性化**
   基于用户历史行为优化互动频率、组件类型和触发位置，实现更精细的观看体验调度。

---

## 项目总结

SDInteractive 不是一个单纯的短剧播放器，也不是一次简单的大模型调用，而是一个将短剧播放、剧情理解、互动打标、Manifest 下发、端上调度、用户行为和 AI 降级机制串联起来的全栈系统。

项目在算法上选择 ASR 分段理解、大模型候选生成、人工精选校准和规则化后处理的混合方案，在工程上通过 Android 原生播放、Ktor Local Server、HTTP Range、Interaction Manifest、AI 后处理和 Docker 部署保证系统可运行、可演示、可复现、可扩展。

其核心目标是探索一种低打断、高反馈、可控 AI 驱动的短剧即时互动体验。
