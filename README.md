# Handmade-CI/CD

CI/CD の学習用レポジトリ．

まずはJenkinsで簡単なC++コードをコンパイルできる状態に．

## 目的
- CI/CDの理解，構築
- Jenkinsの理解，構築
- GroovyによるJenkinsジョブの構築
- 簡単なC++コードのビルド
- 簡単なVulkanコードのビルド
- ローカルGit Pushによるビルドの自動化
- テストの自動化
- 静的解析による検査 (MISRA C++？)
- Windows containerへの対応 (移行)
- 形式的検証の導入 (研究)
- GitLabの導入
- GitLab CIによるC++ビルド用イメージの作成
- 作成したイメージをJenkinsのジョブで使用

## 完了
- Jenkinsの構築
- GroovyによるJenkinsジョブの構築
- ローカルGit Pushによるビルドの自動化
- 簡単なC++コードのビルド
- 出力の簡単なテスト
- 簡単な入力に対する出力テスト
- cppcheckによる静的解析
- cppcheckの警告によってUNSTABLE，FAILUREを分ける
- Vulkanコードのビルド
- Vulkanコードのheadless実行
- Vulkanの描画結果をJenkins artifactとして保存
- GitLabの導入

GitLab CIの設定ファイルは作成済み．Pipelineでの実行確認はこれから行う．

## 既知の問題
- 初回起動時，同じコンテナを使用するジョブを二つ同時に実行するとイメージの作成に失敗する

## 構成
可能な限り再現性を保つため，コンテナ上で動作するようにする．

まずは経験のあるLinuxコンテナで構築する．

>※現状x86_64Linuxでしか完全動作しません！  
bareレポジトリをローカルに作成し，それに対するPushによってJenkinsのビルドがトリガーされるようにするが，bareレポジトリの作成シェルスクリプトはmacOSもしくはLinux用である．  
vulkanのビルドはaarch64環境では動作しないため注意．  
Windows(PowerShell)は今後対応予定．

### コンテナ
主に5つのコンテナで構成される．

#### Jenkins本体
```
Jenkins
  - Jenkins controller
  - Docker CLI
  - Jenkins jobの作成
  - C++ builderの起動
```

#### cppcheckによる静的解析用環境
```cppcheck builder
  - cppcheck:test0
  - cppcheckがインストールされた環境
  - Facthunder/cppcheckをそのまま使用
```

#### C++コードのビルド，実行用環境
```
C++ builder
  - gcc:latest
  - g++
  - /workspaceにマウントされたsrc/hello.cppをコンパイル，実行
  - コンパイル，実行後はコンテナごと破棄
```

#### Vulkanコードのビルド，実行用環境
```
Vulkan builder
  - ubuntu:24.04
  - Vulkan SDK
  - GLFW
  - Slang
  - Xvfbによるheadless実行
  - 描画結果のスクリーンショット保存
```

環境汚染防止の観点から，Jenkinsのコンテナ自身ではビルドせず，`gcc:latest`コンテナが別で実行する．


### Job
`jenkins/init.groovy.d/create-cpp-job.groovy`がJenkins起動時に 
- `cpp-hello` : C++コードの静的解析ビルドと実行，入出力テストを行うジョブ．
- `cppcheck-warning` : cppcheckによる静的解析で警告が出た場合にUNSTABLEとするジョブ．
- `cppcheck-error` : cppcheckによる静的解析でエラーが出た場合にビルド失敗とするジョブ．
- `vulkan` : Vulkanコードをビルドし，headless実行して描画結果をartifactに保存するジョブ．

このjobはローカルのbareレポジトリからレポジトリルートの`Jenkinsfile_*`を読み込む．初回セットアップでは`scripts/setup-local-remote.sh`がコミット済みの内容をローカルbareレポジトリへ反映するため，手動でJenkinsfileだけをpushする必要はない．

これにより，Jenkins上でジョブを手動で構築することなく，構築された状態で起動する．

※起動時に実行されるため，更新後はコンテナの再起動が必要．
```sh
docker compose restart jenkins
```

### ローカルremote
GitHubにはpushせず，レポジトリ内のローカルbareレポジトリをリモートとして使う．

初回にローカルリモートレポジトリを作成し，現在のコミット済みの内容をmirror pushする．

ローカルbareレポジトリの作成と Jenkins のビルドトリガー用の認証トークンの生成用シェルスクリプト
```sh
bash scripts/setup-local-remote.sh
```

以後はローカルリモートにpushする．
```sh
git add (ビルドするファイルへのパス)
```

```sh
git commit -m "コメント"
```

```sh
git push local main
```

ローカルリモートの`post-receive`hookが変更されたファイルを見て，該当するJenkinsジョブを起動する．
例えば`src/hello.cpp`や`Jenkinsfile_hello`が変われば`cpp-hello`，`src/overflow.cpp`や`Jenkinsfile_warning`が変われば`cppcheck-warning`，`src/memleak.cpp`や`Jenkinsfile_error`が変われば`cppcheck-error`，`src/vulkan.cpp`や`src/shader.slang`や`Jenkinsfile_Vulkan`が変われば`vulkan`が動く．

ローカルbareレポジトリからのcheckoutを許可するため，Jenkinsコンテナに`JAVA_TOOL_OPTIONS`で`hudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true` を設定する必要がある．
また，Jenkinsコンテナ内のGitが`/workspace/.local/git/handmade-cicd.git`をcheckoutできるように，Jenkinsイメージ内で`safe.directory`に登録している．

Jenkinsfileを更新した場合は，`git add Jenkinsfile`，`git commit -m "コメント"`，`git push local main`をする．

一方で，`scripts/setup-local-remote.sh`や`jenkins/init.groovy.d/create-cpp-job.groovy`を更新した場合は，`bash scripts/setup-local-remote.sh`を再実行し，必要ならJenkinsを再起動する．(未検証)

CppCheckを使う場合は，`cppcheck/Dockerfile`から`cppcheck:test0`をビルドしてから静的解析を行う．Jenkinsfileではビルドを先に行うようにしている．

### Pipeline
`Jenkinsfile`および`Jenkinsfile_*`はJenkins Pipelineの定義ファイル．
中身はGroovyベースのDeclarative Pipeline．

`cpp-hello`の現在の流れ:

1. `cppcheck:test0`を`cppcheck/Dockerfile`からビルドし，`src/hello.cpp`を静的解析する
2. `cpp-builder:test0`を`builder/cpp/Dockerfile`からビルドする
3. C++ Builderで`src/hello.cpp`を`build/hello`にコンパイルし，実行
4. コンパイル結果を実行後，`build/`を削除し，コンテナを破棄する
5. ローカルリモートへのpushをJenkinsが検知して再実行する

ソースが入力を必要とする場合，無限に終わらない状態となるので，タイムアウトを設定している．

### 静的解析
cppcheckによる静的解析をビルド前に行う．

warningはUNSTABLE，errorはビルド失敗とする．

### Vulkan
`builder/vulkan/Dockerfile`でVulkan SDK入りのビルド・実行用イメージを作り，`Jenkinsfile_Vulkan`で `src/vulkan.cpp` をコンパイルする．
ビルド用コンテナでは `VULKAN_SDK=/opt/vulkansdk/default` を使う．
ビルド前に `src/shader.slang` を `slangc` で `shaders/slang.spv` に変換してから，`src/vulkan.cpp` をコンパイルする．
リンク時は `-L"$VULKAN_SDK/lib"` を付けて `libvulkan.so` を見つけるようにしている．
Vulkan-Hppの構造体をdesignated initializerで初期化しているため，コンパイル時に `VULKAN_HPP_NO_STRUCT_CONSTRUCTORS` を定義している．

Vulkanジョブでは，ビルド後にXvfb上で`build/vulkan`をheadless実行する．
`src/vulkan.cpp`は`CI=true`のときだけ一定時間で自動終了するため，Jenkins上でもジョブが終了する．
実行時に取得したスクリーンショットとログは以下のartifactとして保存される．

```text
artifacts/vulkan/vulkan.png
artifacts/vulkan/run.log
artifacts/vulkan/xvfb.log
```

Jenkinsのビルド結果画面の`Build Artifacts`から`artifacts/vulkan/vulkan.png`を開くと，描画結果を確認できる．
Apple Silicon 環境では Vulkan ジョブの `Prepare Vulkan Builder` だけ `DOCKER_DEFAULT_PLATFORM=linux/amd64` と `DOCKER_BUILDKIT=0` を付けて amd64 版イメージを作る．

## 起動
submoduleであるcppcheckを含めて取得するため，clone時に以下を使う．
```sh
git clone --recurse-submodules https://github.com/ITO1103/handmade-cicd.git
```

既にclone済みの場合は，submoduleを初期化してから使う．
```sh
git submodule update --init --recursive
```

ローカルbareレポジトリを作成し，コミット済みのJenkinsfileやsrc配下のファイルを反映する．
```sh
bash scripts/setup-local-remote.sh
```

コンテナのビルドと起動:
```sh
docker compose up -d --build
```

### Jenkins
Jenkinsの管理画面URL:
```
http://localhost:8080
```

<!-- ※Jenkinsの初期パスワードはランダムに生成されるので，以下のコマンドにて確認する．
```sh
docker compose exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
``` -->

起動<!--し管理者アカウントを作成-->後，`cpp-hello`，`cppcheck-warning`，`cppcheck-error`，`vulkan`という名前のジョブが作成されているので，緑色の再生ボタンを押してジョブを実行．

※Jenkins起動後にローカルリモートを作り直した場合は，Jenkinsを再起動する．
```sh
docker compose restart jenkins
```

`cpp-hello`はジョブの詳細画面から`Console Output`を確認し，`Hello, world!`と表示されているかを確認．
`vulkan`はジョブの詳細画面から`Build Artifacts`を確認し，`artifacts/vulkan/vulkan.png`を開いて三角形が描画されているかを確認．

### GitLab
GitLabの管理画面URL:
```
http://localhost:8929
```
>※GitLab自体が複雑なので起動は遅いです．環境にもよりますが5分程度かかります．

Container Registryは`http://localhost:5050`で公開する．

GitLabの実行時データは`gitlab/`配下に保存されるが，Git管理対象外としている．

GitLabの初期管理者ユーザーは`root`．初期パスワードは以下で確認できる．
```sh
docker compose exec gitlab grep 'Password:' /etc/gitlab/initial_root_password
```

初期パスワードのファイルは，初回起動から24時間経過後のコンテナ再起動で削除される．確認後はGitLab上でパスワードを変更する．

その後のプロジェクト作成画面はスキップして良い．

SSHでGitLabを使用する場合は，SSH公開鍵をGitLabに登録する．
公開鍵がない場合は以下で作成する．
```sh
ssh-keygen -t ed25519
```

公開鍵を確認する．
```sh
cat ~/.ssh/id_ed25519.pub
```

GitLabのユーザーアイコンから`Edit profile` → `Access` → `SSH keys` → `Add new key`を開き，公開鍵を登録する．秘密鍵は登録しない．

#### C++用ビルドイメージ

まずGitLabに`root`でログインし，`Create new` → `New project/repository` → `Create blank project`を開く．名前とパスを`cpp-builder-image`，公開範囲を`Public`にする．`Initialize repository with a README`を選ぶと`main`ブランチができ，すぐにcloneできる．`Settings` → `General`でContainer Registryが有効なことも確認する．

`Settings` → `Repository`で`main`が保護されているか確認する．pushの許可はMaintainerだけなのも確認する．続けて`Settings` → `CI/CD` → `Runners`からProject Runnerを作る．タグは`docker`，保護ブランチ専用にし，タグなしジョブの実行は無効にする．作成直後の登録画面に表示されるRunner認証トークン（`glrt-...`）を使い，Runnerを登録する．画面を閉じた場合は，作成したRunnerの`Register`画面を開く．

Runnerの詳細画面が404になる場合は，ログイン時と同じホスト名で開いているか確認する．IPアドレスでログインした場合は，登録画面のURLもそのIPアドレスにする．

以下のコマンドは，このリポジトリのルートで実行する．`/runner-template.toml`はRunnerコンテナ内のパス．

```sh
docker compose --profile gitlab-ci run --rm gitlab-runner register \
  --url http://localhost:8929 \
  --executor docker \
  --docker-image docker:27.5.1-cli \
  --docker-pull-policy if-not-present \
  --template-config /runner-template.toml
docker compose --profile gitlab-ci up -d gitlab-runner
```

登録中にトークンを聞かれたら，画面に表示されたものを入力する．

次にGitLabのプロジェクトをcloneし，`gitlab/projects/cpp-builder-image`に置いた設定例をコピーしてpushする．コピー後はGitLab側のリポジトリで編集する．以下はこのリポジトリのルートから実行する例．先にSSH公開鍵をGitLabへ登録しておく．

```sh
ssh -T -p 2424 git@localhost
git clone ssh://git@localhost:2424/root/cpp-builder-image.git ../cpp-builder-image
cp -a gitlab/projects/cpp-builder-image/. ../cpp-builder-image/
git -C ../cpp-builder-image add -A
git -C ../cpp-builder-image commit -m "Add C++ builder image"
git -C ../cpp-builder-image push origin main
```

pushするとGitLab CIがDockerfileからイメージを作り，Container Registryへ保存する．結果は`Build` → `Pipelines`で確認する．RunnerはホストのDocker daemonを操作できるため，このプロジェクト専用で使う．

## 注意
学習目的のためセキュリティが甘いです．
特にJenkinsのコンテナにroot権限を与えているため，外部公開する環境ではこの構成をそのまま使わないでください．

## 参考
- Jenkins Dockerイメージ：
  - https://github.com/jenkinsci/docker
- Jenkinsを使用したCI/CDパイプライン構築ブログ：
  - https://www.docker.com/ja-jp/blog/docker-and-jenkins-build-robust-ci-cd-pipelines/
- Jenkins Pipelineドキュメント：
  - https://www.jenkins.io/doc/book/pipeline/
- Jenkinsfileの書き方:
  - https://www.jenkins.io/doc/book/pipeline/jenkinsfile/
- GitLab Dockerインストール（公式）:
  - https://docs.gitlab.com/install/docker/installation/
- 静的解析ツール cppcheckの導入，使用方法:
  - https://kinoshita-hidetoshi.github.io/Programing-Items/C++/etc/cppcheck.html
- cppcheckおよびMISRA C++のDocker:
  - https://github.com/Facthunder/cppcheck.git
- Vulkanのドキュメント(環境構築)
  - https://docs.vulkan.org/tutorial/latest/02_Development_environment.html#_linux
