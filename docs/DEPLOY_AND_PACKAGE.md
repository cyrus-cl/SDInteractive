# 部署后端并打包可分发 APK

## 核心原则

APK 不能依赖你的电脑。因为视频不能放进 `assets`，所以 APK 下载即用的前提是：

1. 后端部署到公网服务器。
2. 视频文件放在公网后端能读取的位置。
3. 打包 APK 时把公网后端地址写入 `BuildConfig.DEFAULT_SERVER_URL`。

## 1. 准备云服务器

建议最低配置：

```text
Ubuntu 22.04 / 24.04
2 核 4G
80GB 磁盘以上
开放端口：22、8081
```

如果有域名和 HTTPS，后续再加 Nginx/Caddy 反代到 `8081`。比赛演示阶段可以先用：

```text
http://服务器公网IP:8081/
```

## 2. 上传项目和视频

在你的电脑上压缩并上传：

```text
D:\SDInteractive
```

服务器目录建议：

```text
/opt/sdinteractive
```

视频文件保持在：

```text
/opt/sdinteractive/video/第1集.mp4
/opt/sdinteractive/video/第2集.mp4
...
```

## 3. 服务器安装 Docker

在服务器执行：

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
```

## 4. 启动公网后端

进入项目目录：

```bash
cd /opt/sdinteractive
export PUBLIC_BASE_URL=http://你的服务器公网IP:8081
sudo docker compose up -d --build
```

检查：

```bash
curl http://你的服务器公网IP:8081/api/dramas
curl http://你的服务器公网IP:8081/api/dramas/drama_001/episodes
curl http://你的服务器公网IP:8081/api/episodes/ep_001/play
curl -I -H "Range: bytes=0-15" http://你的服务器公网IP:8081/static/videos/ep_001.mp4
```

最后一个命令必须看到：

```text
206 Partial Content
Accept-Ranges: bytes
Content-Range: bytes 0-15/...
```

## 5. 打包可分发 APK

回到你的 Windows 电脑项目目录：

```powershell
cd D:\SDInteractive
$env:JAVA_HOME='D:\CodeDevelopment\JDK25'
$env:ANDROID_HOME='D:\SDInteractive\.android-sdk'
$env:ANDROID_SDK_ROOT='D:\SDInteractive\.android-sdk'
.\gradlew.bat :android-app:assembleRelease -PserverBaseUrl=http://你的服务器公网IP:8081/
```

输出文件：

```text
D:\SDInteractive\android-app\build\outputs\apk\release\android-app-release.apk
```

这个 APK 默认连接公网后端，别人安装后不需要改服务地址。

## 6. 本机调试 APK

手机数据线调试：

```powershell
adb reverse tcp:8081 tcp:8081
.\gradlew.bat :android-app:assembleDebug
adb install -r D:\SDInteractive\android-app\build\outputs\apk\debug\android-app-debug.apk
```

本机调试地址：

```text
http://127.0.0.1:8081/
```

公网发布地址：

```text
http://你的服务器公网IP:8081/
```
