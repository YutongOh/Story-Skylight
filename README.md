# Story Skylight V1–V4 Web Demo

静态 HTML 演示，复刻 Android `Story Skylight V1–V4` playground（Feed ↔ Inbox + Story Reveal + Preview）。

## 入口

| 文件 | 说明 |
|------|------|
| `index.html` | 四组实验列表（V1–V4） |
| `preview-v1.html` … `preview-v4.html` | 各版本默认预览壳（分别默认 V1–V4） |
| `preview.html` | 通用预览壳（默认 V1，可用 `?v=v2` 切换） |

## 在线预览（GitHub Pages）

- 列表入口：<https://yutongoh.github.io/Story-Skylight/>
- V1：<https://yutongoh.github.io/Story-Skylight/preview-v1.html>
- V2：<https://yutongoh.github.io/Story-Skylight/preview-v2.html>
- V3：<https://yutongoh.github.io/Story-Skylight/preview-v3.html>
- V4：<https://yutongoh.github.io/Story-Skylight/preview-v4.html>

## 本地预览

**必须在 demo 目录下启动 HTTP 服务**（不能直接双击 HTML 用 `file://` 打开，否则素材和 iframe 会加载失败）。

```bash
cd exports/story-skylight-v123-demo
python3 -m http.server 8770
```

打开：

- V1–V4 默认预览：`http://127.0.0.1:8770/preview-v1.html` … `preview-v4.html`
- 列表入口：http://127.0.0.1:8770/index.html
- 桌面预览：**鼠标拖拽**模拟触摸滑动，**单击**触发按钮；鼠标进入手机区域显示触控小球

## 四组差异（与 Android 对齐）

| 变体 | 模式 | 进入 Inbox | 展开 | 收起 | 特色 |
|------|------|-----------|------|------|------|
| **V1** | Integrated（锁定） | 默认已展开 | 不可收起 | — | 天窗随列表滚动，下拉刷新 |
| **V2** | Overlay | 收起 → 400ms 自动展开 | 拖拽即展开（0dp） | 上滑 ≥16dp | 展开后可链式下拉刷新 |
| **V3** | Integrated | 收起 → 400ms 自动展开 | 到顶下拉 ≥32dp | 上滑 ≥16dp | 整页滚动 + 半展开回弹 |
| **V4** | Overlay | 收起 → 400ms 自动展开 | 下拉 ≥24dp 松手 | 上滑 ≥30dp | Story slide + Release hint |

## 交互流程

1. **默认 Feed** — 与 Effect Loading 1.2s 一致
2. **点 Inbox Tab** — 150ms crossfade 进入 Inbox
3. **V2/V3/V4** — 进入 Inbox 后 400ms 自动展开天窗（V3 需 `startExpanded + autoExpandOnEnter`）
4. **点 Story 头像** — Story Preview（进度条 + 横滑）
5. **点 Create（Skylight）** — Add to Story sheet
6. **Feed Create** — Album V1

## 重新生成

```bash
python3 export_story_skylight_v123.py
```

## 目录

```
story-skylight-v123-demo/
├── preview.html / preview-v1.html … preview-v4.html
├── preview.js / preview.css
├── index.html
├── export_story_skylight_v123.py
├── shared/
│   ├── skylight.css / skylight-inbox.css / skylight-core.js
│   └── assets/          # web 素材（inbox / story / feed / figma / video）
└── android-reference/     # Android 源码 + res 素材镜像
    ├── kotlin/
    └── res/
```

## 与 Android 对齐

- 设计帧：360×800
- Reveal：`MaxHeight=118dp`；V2/V3 `maxPull=118dp`；V1/V4 `maxPull=72dp`
- 动效：`ExpandMs=450`，`CollapseMs=350`，`SettleBackMs=250`，`AutoExpandDelayMs=400`
- 阈值：V2/V3 `PushThreshold=16dp`；V1/V4 `PushThreshold=30dp`；V3 `PullThreshold=32dp`；V1/V4 `PullThreshold=24dp`（V2 展开为 0）
- V3 预览壳双指滚动：`wheelScrollDamping=0.72`（Web 专用）
