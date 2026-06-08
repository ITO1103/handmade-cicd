FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        wget \
        curl \
        git \
        cmake \
        build-essential \
        libglfw3-dev \
        libvulkan-dev \
        ninja-build \
        libglm-dev \
        pkg-config \
        xz-utils \
        ca-certificates \
        mesa-vulkan-drivers \
        vulkan-tools \
        xvfb \
        x11-apps \
        imagemagick && \
    rm -rf /var/lib/apt/lists/*

# Vulkanの依存関係をチュートリアルからインストール
RUN git clone https://github.com/KhronosGroup/Vulkan-Tutorial.git && \
    cd Vulkan-Tutorial && \
    bash scripts/install_dependencies_linux.sh

# Vulkan SDKをダウンロード，インストール
RUN mkdir -p /opt/vulkansdk && \
    curl -L https://sdk.lunarg.com/sdk/download/1.4.350.1/linux/vulkansdk-linux-x86_64-1.4.350.1.tar.xz \
        -o /tmp/vulkansdk-linux-x86_64-1.4.350.1.tar.xz && \
    tar -xf /tmp/vulkansdk-linux-x86_64-1.4.350.1.tar.xz -C /opt/vulkansdk && \
    ln -sfn /opt/vulkansdk/1.4.350.1/x86_64 /opt/vulkansdk/default && \
    rm -f /tmp/vulkansdk-linux-x86_64-1.4.350.1.tar.xz

ENV VULKAN_SDK=/opt/vulkansdk/default
ENV PATH="$VULKAN_SDK/bin:${PATH}"
ENV LD_LIBRARY_PATH="$VULKAN_SDK/lib"
ENV VK_LAYER_PATH="$VULKAN_SDK/etc/vulkan/explicit_layer.d:$VULKAN_SDK/share/vulkan/explicit_layer.d"

WORKDIR /workspace
