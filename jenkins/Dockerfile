FROM jenkins/jenkins:lts-jdk21

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        docker-cli \
        git \
    && git config --system --add safe.directory /workspace/.local/git/handmade-cicd.git \
    && rm -rf /var/lib/apt/lists/*

USER jenkins

# Jenkins起動前に必要なプラグインをインストール
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt

RUN jenkins-plugin-cli \
    --plugin-file /usr/share/jenkins/ref/plugins.txt