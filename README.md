# SDInteractive

> 字节跳动 AI 全栈挑战赛项目作业：基于短剧剧情的即时互动激发

SDInteractive 是一个可以在本地部署体验的短剧互动 App。你只需要在电脑上启动 Local Server，再把手机和电脑连到同一个 Wi-Fi，就能在 Android App 里播放短剧，并在剧情关键节点看到弹出的互动内容。

项目把短剧视频托管、手机端播放、剧情互动、用户行为记录、AI 辅助打点和人物识别放在同一套流程里。普通用户可以把它当作一个“边看短剧边互动”的 App 来体验；开发者也可以从源码中看到端侧播放、服务端接口和 AI 后处理是如何配合工作的。

## 目录

- [项目定位](#项目定位)
- [核心亮点](#核心亮点)
- [系统架构](#系统架构)
- [技术选型](#技术选型)
- [项目结构](#项目结构)
- [快速运行](#快速运行)
- [APK 体验](#apk-体验)
- [AI 配置](#ai-配置)
- [接口概览](#接口概览)
- [测试与构建](#测试与构建)
- [公开发布安全说明](#公开发布安全说明)

## 项目定位

短剧内容天然具有强情绪、高反转、快节奏的特点。SDInteractive 尝试在播放过程中识别剧情节拍，并在合适时间点插入“轻量但即时”的互动，例如情绪按钮、剧情竞猜、知识卡片、高能预警、人物识别、演技打分、名场面收藏等。

项目当前以 Android App 形式交付，后端在本机或局域网服务器运行。这样的使用方式有三个好处：

- 视频不需要打入 APK，减少安装包体积，也便于替换短剧素材。
- Local Server 可以统一管理剧集目录、播放地址、用户行为、AI 能力和静态视频分发。
- 手机真机可在同一 Wi-Fi 内访问电脑服务，便于本地体验、现场展示和调试。

## 核心亮点

### 剧情互动激发

- 内置 39 个短剧时间轴互动事件，覆盖情绪反馈、剧情竞猜、竞猜结果、知识解释、高能预警、演技评分、数值助推、人物关系、名场面收藏等类型。
- 支持 `Fixed`、`Range`、`EpisodeEnding` 三类触发器，适配固定剧情点、可交互时间窗和剧集结尾悬念。
- 支持互动优先级调度，避免多个组件同时抢占主画面。
- 支持互动结果持久化，例如竞猜答案、评分记录、数值状态和收藏片段。

### AI 算法闭环

- **互动点位打标**：服务端接收 ASR 分段，调用 Ark/Doubao 兼容模型生成候选互动点，并归一化为可落库的结构化 JSON。
- **本地启发式 fallback**：未配置模型或模型输出不可解析时，基于台词关键词、剧情语义和窗口密度生成可用候选，保证演示稳定。
- **人物识别**：Android 端截取当前视频帧，服务端结合人物目录、剧情时间线候选和视觉模型结果识别当前画面人物。
- **置信度约束**：人物识别通过候选集、人物目录、置信度阈值和视觉证据过滤，降低模型幻觉风险。

### 工程实现闭环

- Android 端使用 Jetpack Compose 构建短视频式竖滑播放体验。
- Media3 ExoPlayer 播放后端托管的 MP4，服务端支持 HTTP Range，满足移动端拖动进度和流式播放。
- Ktor Local Server 提供内容目录、播放信息、行为上报、互动上报、AI 接口和静态视频服务。
- 支持 Windows 本机运行，也支持 Docker Compose 部署后端。
- 通过单元测试覆盖互动调度、视频目录、Range 播放、AI 后处理、人物识别和本地存储等关键路径。

## 系统架构

```text
Android App
  ├─ Jetpack Compose UI
  ├─ Media3 ExoPlayer
  ├─ Interaction Scheduler / Coordinator
  ├─ Local interaction storage
  └─ Retrofit + OkHttp
          │
          │ HTTP over LAN
          ▼
Local Server (Ktor / Netty)
  ├─ Drama / Episode / Play APIs
  ├─ MP4 static hosting with Range support
  ├─ User action and profile APIs
  ├─ Interaction event and QoE APIs
  ├─ AI interaction tagging
  └─ AI person insight / person identification
          │
          │ Optional
          ▼
Ark-compatible Chat Completions / Vision Model
```

## 技术选型

| 模块 | 技术 | 选择原因 |
| --- | --- | --- |
| Android UI | Kotlin, Jetpack Compose, Material3 | 适合构建状态驱动、可组合的短视频互动界面 |
| 视频播放 | AndroidX Media3 ExoPlayer | 支持远程 MP4、缓冲状态、进度采样、seek 和播放控制 |
| 网络访问 | Retrofit, OkHttp | Android API 调用稳定，便于统一处理服务地址和超时 |
| 后端服务 | Kotlin, Ktor, Netty | 轻量、启动快，适合 Local Server 和局域网演示 |
| 序列化 | kotlinx.serialization, Gson | 服务端强类型 JSON，客户端快速接入 |
| AI 接入 | Ark-compatible Chat Completions | 兼容豆包/方舟文本与视觉模型能力 |
| 部署 | Gradle, Docker Compose | 同时覆盖本机开发和容器化部署 |
| 测试 | JUnit, Ktor test host | 覆盖调度、播放、AI 后处理、人物识别和接口行为 |

## 项目结构

```text
SDInteractive/
  android-app/              # Android 客户端源码
  server/                   # Ktor Local Server 源码
  docs/DEPLOY_AND_PACKAGE.md# 公网部署与 APK 打包说明
  gradle/                   # Gradle Wrapper
  release/                  # 可公开上传的演示 APK
  .env.example              # 本地 AI 配置模板，不含真实密钥
  docker-compose.yml        # 后端容器启动配置
  Dockerfile                # 后端镜像构建配置
```

以下目录或文件只用于本地运行和调试，不应上传到公开仓库：

```text
.env
local.properties
.android-sdk/
.gradle/
.kotlin/
build/
**/build/
video/
*.log
docs/superpowers/
交互按钮ui设计/
```

## 快速运行

### 环境要求

- Windows 10/11
- JDK 25
- Android SDK，项目当前使用 `compileSdk 36`
- Android 8.0 及以上真机或模拟器
- 真机局域网访问时，手机和电脑需要处于同一 Wi-Fi

### 1. 准备短剧视频

公开仓库不包含原始短剧视频素材。请在项目根目录创建 `video/`，并按剧集顺序放置 MP4 文件：

```text
video/
  第1集.mp4
  第2集.mp4
  第3集.mp4
  ...
```

服务端会扫描 `video/`，生成 `ep_001`、`ep_002` 等播放 ID，并为每集提供播放地址。

### 2. 启动 Local Server

```powershell
cd D:\SDInteractive
$env:JAVA_HOME='D:\CodeDevelopment\JDK25'
$env:VIDEO_DIR='D:\SDInteractive\video'
.\gradlew.bat :server:run
```

默认端口为 `8081`。启动后可以检查：

```powershell
Invoke-RestMethod http://localhost:8081/api/dramas
Invoke-RestMethod http://localhost:8081/api/dramas/drama_001/episodes
Invoke-RestMethod http://localhost:8081/api/episodes/ep_001/play
curl.exe -I -H "Range: bytes=0-15" http://localhost:8081/static/videos/ep_001.mp4
```

最后一个命令应返回 `206 Partial Content`，表示视频 Range 请求可用。

### 3. 局域网真机访问

先查看电脑在局域网内的 IP：

```powershell
ipconfig
```

假设电脑 IP 为 `192.168.1.100`，启动服务前设置：

```powershell
$env:PUBLIC_BASE_URL='http://192.168.1.100:8081'
$env:VIDEO_DIR='D:\SDInteractive\video'
.\gradlew.bat :server:run
```

然后在 Android App 的服务地址中填写：

```text
http://192.168.1.100:8081/
```

如果真机无法访问，请检查 Windows 防火墙是否允许局域网访问 `8081` 端口。

### 4. Docker 启动后端

```powershell
cd D:\SDInteractive
$env:PUBLIC_BASE_URL='http://192.168.1.100:8081'
docker compose up -d --build
```

Docker 模式同样要求本地存在 `video/`，并通过 volume 挂载到容器内。

## APK 体验

公开演示 APK 放在：

```text
release/SDInteractive-demo-release.apk
```

安装后，可在 App 的“我的”页面修改服务地址为当前电脑的局域网地址，例如：

```text
http://192.168.1.100:8081/
```

重新打包 APK：

```powershell
cd D:\SDInteractive
$env:JAVA_HOME='D:\CodeDevelopment\JDK25'
$env:ANDROID_HOME='D:\SDInteractive\.android-sdk'
$env:ANDROID_SDK_ROOT='D:\SDInteractive\.android-sdk'
.\gradlew.bat :android-app:assembleRelease -PserverBaseUrl=http://192.168.1.100:8081/
```

输出路径：

```text
android-app/build/outputs/apk/release/android-app-release.apk
```

模拟器调试可使用默认地址 `http://10.0.2.2:8081/`。真机 USB 调试也可以使用：

```powershell
adb reverse tcp:8081 tcp:8081
```

然后在 App 中填写：

```text
http://127.0.0.1:8081/
```

## AI 配置

AI 能力是可选项。未配置 API Key 时，服务端会自动使用本地 fallback，核心播放和互动演示仍然可运行。

如需启用模型能力，请复制 `.env.example` 为 `.env`，并只在本地填写真实配置：

```dotenv
ARK_API_KEY=your_api_key_here
ARK_MODEL=your_model_or_endpoint_id
ARK_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
```

支持的兼容变量：

```text
ARK_API_KEY / DOUBAO_API_KEY / VOLCENGINE_API_KEY / APIKEY
ARK_MODEL / DOUBAO_MODEL / ARK_ENDPOINT_ID / EP / MODEL
ARK_BASE_URL / DOUBAO_BASE_URL / VOLCENGINE_BASE_URL
```

请不要提交 `.env`、真实 API Key、模型密钥或任何私有配置。公开仓库只保留 `.env.example`。

## 接口概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/guest` | 游客登录 |
| `GET` | `/api/dramas` | 获取短剧列表 |
| `GET` | `/api/dramas/{dramaId}/episodes` | 获取剧集列表 |
| `GET` | `/api/episodes/{episodeId}/play` | 获取播放地址 |
| `GET` | `/api/episodes/{episodeId}/interaction-manifest` | 获取剧集互动清单 |
| `GET` | `/static/videos/{episodeId}.mp4` | MP4 Range 播放 |
| `POST` | `/api/users/{userId}/actions` | 点赞、收藏、评论、分享 |
| `GET` | `/api/users/{userId}/profile` | 用户互动资产 |
| `POST` | `/api/playback/progress` | 播放进度上报 |
| `POST` | `/api/qoe/events` | QoE 事件上报 |
| `POST` | `/api/interactions/events` | 互动事件上报 |
| `POST` | `/api/ai/interaction-tagging` | AI 互动点位打标 |
| `POST` | `/api/ai/person-identify` | 当前帧人物识别 |
| `POST` | `/api/ai/person-insight` | 人物即时看点生成 |
| `POST` | `/api/ai/branch-content` | 预留剧情分支生成任务 |
| `GET` | `/api/ai/branch-content/{taskId}` | 查询分支生成任务状态 |

## 测试与构建

运行全部测试：

```powershell
$env:JAVA_HOME='D:\CodeDevelopment\JDK25'
.\gradlew.bat test
```

构建 Android debug 包：

```powershell
$env:JAVA_HOME='D:\CodeDevelopment\JDK25'
$env:ANDROID_HOME='D:\SDInteractive\.android-sdk'
$env:ANDROID_SDK_ROOT='D:\SDInteractive\.android-sdk'
.\gradlew.bat :android-app:assembleDebug
```

构建后端分发包：

```powershell
$env:JAVA_HOME='D:\CodeDevelopment\JDK25'
.\gradlew.bat :server:installDist
```

已有测试覆盖重点：

- 互动调度器触发、优先级、暂停、seek、剧集切换和结尾互动。
- 39 个内置互动事件的唯一性和组件合法性。
- 本地互动记录的编解码、去重和异常容错。
- Ktor 接口、视频目录解析、MP4 Range 请求和用户行为记录。
- AI 互动打标的 JSON 归一化、fallback 和窗口密度控制。
- 人物识别的人物目录、视觉结果过滤、时间线 fallback 和单帧证据约束。

## 公开发布安全说明

本仓库按公开发布策略整理：

- 不提交 `.env`、`local.properties`、keystore、真实 API Key、模型密钥或任何机器私有配置。
- 不提交 `.android-sdk/`、`.gradle/`、`.kotlin/`、`build/`、`**/build/`、日志文件等中间产物。
- 不提交原始短剧视频素材，`video/` 仅作为本地运行目录。
- 不提交内部过程文档、临时设计图、调试截图和工具缓存目录。
- 仅保留公开复现工程需要的源码、Gradle 配置、Docker 配置、README、部署说明、配置模板和演示 APK。

上传 GitHub 前建议再次执行：

```powershell
git status --ignored
git ls-files
```

确认将要提交的文件中没有 `.env`、真实密钥、视频素材、日志和构建中间文件。API Key 泄露风险很高，一旦误传应立即撤销旧密钥并重新生成。
