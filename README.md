# Story Skylight V2 Web Demo

静态 HTML 演示，复刻 Android Story Skylight **V2** playground（Feed ↔ Inbox + Overlay Story Reveal + 链式刷新）。

## 入口

| 文件 | 说明 |
|------|------|
| `index.html` | V2 预览壳（GitHub Pages 根入口） |
| `preview-v2.html` | V2 预览壳（与 index 相同） |

## 在线预览（GitHub Pages）

- **V2 Demo**：<https://yutongoh.github.io/Story-Skylight/>
- 备用链接：<https://yutongoh.github.io/Story-Skylight/preview-v2.html>

## 本地预览

**必须在 demo 目录下启动 HTTP 服务**（不能直接双击 HTML 用 `file://` 打开）。

```bash
cd exports/story-skylight-v123-demo
python3 -m http.server 8770
```

打开：http://127.0.0.1:8770/

桌面预览：**鼠标拖拽**模拟触摸滑动，**单击**触发按钮。

## V2 行为

| 项目 | 说明 |
|------|------|
| 模式 | Overlay |
| 进入 Inbox | 收起 → 400ms 自动展开 |
| 展开 | 拖拽即展开（0dp） |
| 收起 | 上滑 ≥16dp |
| 特色 | 展开后可链式下拉刷新 |

## 重新生成

```bash
python3 export_story_skylight_v123.py
```

仅发布 `variants/v2/` 到 GitHub Pages bundle。

## 目录

```
story-skylight-v123-demo/
├── index.html / preview-v2.html
├── preview.js / preview.css
├── export_story_skylight_v123.py
├── shared/
│   ├── skylight.css / skylight-inbox.css / skylight-core.js
│   └── assets/
├── variants/v2/
└── android-reference/
```
