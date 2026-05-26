# modpack-export 容器

## 分工

| 组件 | 位置 |
|------|------|
| 镜像 | JRE、xvfb、`.cache`、field-guide jar、entrypoint |
| **挂载** | `Modpack-Modern/`（先跑 `scripts/checkout-modpack-latest-release.sh` 对齐最新 release） |
| **挂载** | `.cache/`、`export/` |
| 可选挂载 | `FIELD_GUIDE_JAR`、`CLI_JAR` |

## 流程

```bash
./gradlew :forge:reobfJar
./docker/prepare-cache.sh          # 原版 MC：HMC；Forge：Maven installer（不用 HMC forge）
cd Modpack-Modern && java -jar pakku.jar fetch && cd ..
./docker/build-image.sh
./docker/run-export.sh --no-build
```

若本机 Prism 已装好 1.20.1 + Forge 47.4.13，可跳过下载：

```bash
COPY_MINECRAFT_FROM="$HOME/.minecraft" ./docker/prepare-cache.sh
```

### 代理（默认探测 `127.0.0.1:7890`）

脚本会检测本地端口；若已有 `HTTP_PROXY`/`HTTPS_PROXY` 则沿用。可强制：

```bash
FG_PROXY=http://127.0.0.1:7890 ./docker/prepare-cache.sh
```

有代理时 `prepare-cache` 会优先用 HeadlessMC 安装 Forge（Prism 元数据可走代理）；失败再回退 Maven installer。

容器内运行会把 `127.0.0.1:7890` 映射为 `host.docker.internal:7890`（macOS）或 `host.containers.internal:7890`（Podman）。

仅重编 mod、不重打镜像：

```bash
./gradlew :forge:reobfJar
FIELD_GUIDE_JAR=forge/build/libs/field-guide-forge-0.1.0.jar ./docker/run-export.sh --no-build
```

## 环境变量

- `FG_EXPORT_MODE`：`closure`（默认）或 `full`
- `FG_JVM_HEAP`：可选（如 `6G`）；默认不设固定 `-Xmx`，与 CI 一样用 `MaxRAMPercentage=70`
- `FG_CONTAINER_MEMORY`：默认 `10g`（`podman run --memory`）

### 退出码 137（Minecraft exited with code: 137）

表示进程被 **OOM** 杀掉。GitHub `ubuntu-latest` 内存大且 CI **没有** `-Xmx4G`；本机 Podman 虚拟机若只有 2–4GB，再设 `-Xmx4G` 会在 GTCEu/KubeJS 加载阶段被 kill。

```bash
# 加大 Podman 虚拟机（macOS 常见）
podman machine stop
podman machine set --memory 12288
podman machine start

# 或显式限制堆并给足容器内存
FG_JVM_HEAP=6G FG_CONTAINER_MEMORY=12g ./docker/run-export.sh --no-build
```

## macOS 说明

脚本兼容系统自带 bash 3.2（不使用 `mapfile`）。若 `prepare-cache.sh` 失败，可先 `bash --version` 确认；也可用 `brew install bash` 后显式用 `/opt/homebrew/bin/bash ./docker/prepare-cache.sh`。
