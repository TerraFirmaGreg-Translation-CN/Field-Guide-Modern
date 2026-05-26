# modpack-export:<6位 git commit>
# 镜像：JRE + xvfb + .cache + field-guide jar + entrypoint
# Modpack-Modern 在运行时挂载，不打进镜像（子模块持续更新）

FROM eclipse-temurin:17-jammy

ENV WORKDIR=/workspace \
    MODPACK=Modpack-Modern \
    FG_EXPORT_DIR=/workspace/export \
    ALSOFT_DRIVERS=null

WORKDIR /workspace

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        xvfb x11-xserver-utils \
        libgl1 libgl1-mesa-dri \
        libopenal1 \
        python3 \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /workspace/export /workspace/Modpack-Modern

COPY .cache/ /workspace/.cache/
COPY forge/build/libs/field-guide-forge-*.jar /workspace/forge/build/libs/
COPY forge/config/export-fml.toml /workspace/forge/config/export-fml.toml
COPY forge/config/export-forge-client.toml /workspace/forge/config/export-forge-client.toml
COPY docker/entrypoint.sh /entrypoint.sh
COPY docker/lib/versions.sh /workspace/docker/lib/versions.sh

RUN chmod +x /entrypoint.sh \
    && mkdir -p /workspace/forge/build/libs

ENTRYPOINT ["/entrypoint.sh"]
