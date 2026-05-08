# 海康视频监控平台

基于 Spring Boot 3 + FFmpeg + flv.js 的海康威视视频监控 Web 应用，支持实时预览、录像回放、云台控制、手动抓图和手动录像。

## 功能特性

- **实时预览** — 后端获取 RTSP 地址，FFmpeg 实时转封装为 HTTP-FLV，浏览器播放
- **录像回放** — 按时间段查询录像并通过 HTTP-FLV 回放
- **云台控制** — 方向、变倍、聚焦，长按操作
- **手动抓图** — 平台侧抓图并展示/下载
- **手动录像** — 启动/停止平台侧录像
- **暗色主题** — 现代化深色界面，紧凑布局

## 技术架构

```
浏览器 ──(HTTP-FLV)──▶ Spring Boot ──(FFmpeg)──▶ RTSP ──▶ 海康 iSecure Center
   │                      │
   │  flv.js 播放         │  API 调用（Artemis SDK）
   │                      │
   └── 云台/抓图/录像 ────┘
```

| 组件 | 技术 |
|------|------|
| 后端 | Spring Boot 3.3.5, Java 21, Artemis HTTP Client |
| 转码 | FFmpeg（H.264 使用 copy 模式，零 CPU 开销） |
| 前端 | 原生 HTML/CSS/JS, flv.js, Lucide Icons, Inter 字体 |

## 快速开始

### 1. 配置

修改 `src/main/resources/application.yml`：

```yaml
hikvision:
  host: https://你的平台地址:1443
  app-key: 你的AppKey
  app-secret: 你的AppSecret
```

或通过环境变量：

```bash
export HIKVISION_HOST=https://你的平台地址:1443
export HIKVISION_APP_KEY=你的AppKey
export HIKVISION_APP_SECRET=你的AppSecret
```

### 2. 安装 FFmpeg

**Linux：**
```bash
apt install -y ffmpeg        # Ubuntu/Debian
yum install -y ffmpeg        # CentOS/RHEL
```

**Windows：**
项目已内置 `ffmpeg/` 目录，或从 https://www.gyan.dev/ffmpeg/builds/ 下载。

### 3. 打包运行

```bash
# 打包
mvn package -DskipTests

# 运行
java -jar target/hk-video-0.0.1-SNAPSHOT.jar
```

浏览器访问 `http://localhost:8080`

## 部署

### 部署文件清单

```
/opt/hk-video/
├── hk-video-0.0.1-SNAPSHOT.jar   # 主程序
├── startup.sh                    # Linux 启动脚本
├── startup.bat                   # Windows 启动脚本
└── ffmpeg/                       # Windows 需要（Linux 用系统安装）
```

### 启动脚本

**Linux：**
```bash
chmod +x startup.sh
./startup.sh start      # 启动
./startup.sh stop       # 停止
./startup.sh restart    # 重启
./startup.sh status     # 查看状态
./startup.sh log        # 实时日志
```

**Windows：**
```cmd
startup.bat start       # 启动
startup.bat stop        # 停止
startup.bat restart     # 重启
startup.bat status      # 查看状态
startup.bat log         # 实时日志
```

### 配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `server.port` | 8080 | 服务端口 |
| `hikvision.host` | — | 海康平台地址 |
| `hikvision.app-key` | — | API Key |
| `hikvision.app-secret` | — | API Secret |
| `hikvision.ffmpeg.path` | ffmpeg | FFmpeg 可执行文件路径 |
| `hikvision.ffmpeg.max-streams` | 10 | 最大并发转码路数 |
| `hikvision.ffmpeg.timeout` | 300 | 单路超时（秒） |
| `hikvision.ffmpeg.video-codec` | copy | 视频编码（H.264 用 copy，H.265 改 libx264） |
| `hikvision.default-preview.protocol` | rtsp | 取流协议 |

所有参数均可通过启动命令 `--参数名=值` 或环境变量覆盖。

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/hikvision/cameras` | 监控点列表（分页） |
| POST | `/api/hikvision/cameras/{id}/preview` | 获取预览地址 |
| GET | `/api/hikvision/cameras/{id}/live.flv` | 实时预览 HTTP-FLV 流 |
| GET | `/api/hikvision/cameras/{id}/playback.flv` | 录像回放 HTTP-FLV 流 |
| POST | `/api/hikvision/cameras/{id}/playback` | 获取回放地址 |
| POST | `/api/hikvision/cameras/{id}/ptz` | 云台控制 |
| POST | `/api/hikvision/cameras/{id}/capture` | 手动抓图 |
| POST | `/api/hikvision/cameras/{id}/record/start` | 开始录像 |
| POST | `/api/hikvision/cameras/{id}/record/stop` | 停止录像 |

## 项目结构

```
src/main/java/com/example/hkvideo/
├── config/
│   └── HikvisionProperties.java     # 配置属性
├── ffmpeg/
│   └── FfmpegStreamService.java      # FFmpeg 进程管理
├── hikvision/
│   ├── HikvisionOpenApiClient.java   # 海康 API 客户端
│   └── HikvisionVideoService.java    # 视频业务逻辑
└── web/
    ├── HikvisionController.java      # REST 接口
    ├── StreamController.java         # HTTP-FLV 流端点
    └── dto/                          # 请求/响应 DTO

src/main/resources/
├── application.yml                   # 配置文件
└── static/
    ├── index.html                    # 前端页面
    ├── styles.css                    # 暗色主题样式
    └── app.js                        # 前端逻辑
```
