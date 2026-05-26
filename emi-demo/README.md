# emi.js Demo

浏览器内预览 Field Guide 导出的 EMI layout（schema v2）。用于审查 **独立库** `emi.js` 的渲染结果。

## 准备数据

```bash
cd Field-Guide-Modern/emi-demo
chmod +x link-export.sh
./link-export.sh ~/Downloads/guide-export\ \(4\)
```

或在页面顶栏把 **Export 根路径** 设为绝对 URL 路径（需本地 HTTP 服务，不能 `file://` 直接开）。

也可 URL 参数：`?base=http://127.0.0.1:8765/export`

## 启动

```bash
cd Field-Guide-Modern/emi-demo
python3 -m http.server 8765
```

浏览器打开 <http://127.0.0.1:8765/>

| 页面 | URL | 用途 |
|------|-----|------|
| Review | `/index.html` | 单条配方 + 三栏对比 |
| **Perf** | `/perf.html` | **全配方网格 + 视口懒加载**（性能测试） |

## 功能

### Review (`index.html`)

- 左侧配方列表；右侧点击后 `data-recipe-id` + `mountAll`

### Perf (`perf.html`)

- 列出 export 内全部 EMI layout（可筛选）
- 默认 `mountAll({ lazy: true })`：滚入视口附近才加载 layout
- 顶栏：`mounted / failed / pending` 与耗时
- **立即挂载剩余**：`flush()` 一次性挂载未进入视口的块

## 文件

| 文件 | 说明 |
|------|------|
| `emi.js` | 库（`mountAll` 支持 `lazy: true`） |
| `emi.css` | 库样式 |
| `demo-app.js` | Review 页 |
| `perf-app.js` | Perf 全列表懒加载页 |
| `perf.html` / `perf.css` | 性能测试页 |
| `index.html` | Review 入口 |
| `export/` | guide-export symlink |

设计说明见工作区 [`docs/design/emi-js-library.md`](../../docs/design/emi-js-library.md)。
