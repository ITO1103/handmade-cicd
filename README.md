# Handmade-CI/CD

CI/CD の学習用レポジトリ．

まずはJenkinsで簡単なC++コードをコンパイルできる状態に．

## 目的
- CI/CDの理解，構築
- Jenkinsの理解，構築
- GroovyによるJenkinsジョブの構築
- 簡単なC++コードのビルド
- 簡単なVulkanコードのビルド
- GitLabへのPushによるビルドの自動化
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
- GitLab CIでC++ビルド用とcppcheck用のイメージを作成し，Container Registryへ保存
- JenkinsのC++ジョブでContainer Registryに保存されたイメージを使用
- GitLab CIでVulkan用イメージを作成し，JenkinsのVulkanジョブで使用
- GitLabからのソース取得と，Pushによるジョブの起動

## 構成
可能な限り再現性を保つため，コンテナ上で動作するようにする．

まずは経験のあるLinuxコンテナで構築する．

>※現状x86_64Linuxでしか完全動作しません！  
vulkanのビルドはaarch64(Apple Silicon)環境では動作しないため注意．
Windows(PowerShell)は今後対応予定．

### コンテナ
Jenkins，GitLab，GitLab Runnerと，各ジョブで使うコンテナで構成する．

#### GitLab
```text
GitLab CE
  - Web UI : 8929番
  - Git over SSH : 2424番
  - Container Registry : 5050番
  - C++ビルド用，cppcheck用，Vulkan用のイメージを保存
```

GitLabの実行時データは`gitlab/`配下に保存する．Git管理対象外としている．

#### GitLab Runner
```text
GitLab Runner
  - Project Runner
  - Docker executor
  - GitLab CIでC++ビルド用，cppcheck用，Vulkan用のイメージを作成し，Registryへpush
```

RunnerはホストのDocker socketを使う．Runner本体とCIのジョブはhost networkingでGitLabとRegistryへ接続する．

#### Jenkins本体
```
Jenkins
  - Jenkins controller
  - Docker CLI
  - Jenkins jobの作成
  - ビルド用コンテナの起動
```

#### cppcheckによる静的解析用環境
```
cppcheck builder
  - localhost:5050/root/cpp-builder-image/cppcheck:latest
  - GitLab CIで作成したcppcheck入りのイメージ
```

#### C++コードのビルド・実行用環境
```
C++ builder
  - localhost:5050/root/cpp-builder-image:latest
  - gcc:latestをベースにGitLab CIで作成
  - g++
  - Jenkinsの作業ディレクトリにcheckoutしたsrc/hello.cppをコンパイル，実行
  - コンパイル，実行後はコンテナごと破棄
```

#### Vulkanコードのビルド・実行用環境
```
Vulkan builder
  - localhost:5050/root/cpp-builder-image/vulkan:latest
  - ubuntu:24.04をベースにGitLab CIで作成
  - Vulkan SDK
  - GLFW
  - Slang
  - Xvfbによるheadless実行
  - 描画結果のスクリーンショットをJenkinsのartifactとして保存
```

環境汚染防止の観点から，Jenkinsのコンテナ自身ではビルドせず，ビルド用コンテナを別に起動する．JenkinsとRunnerはホストのDocker daemonを使用する．


### Job
`jenkins/init.groovy.d/create-cpp-job.groovy`がJenkins起動時に以下のジョブを作成する．

- `cpp-hello` : C++コードの静的解析，ビルドと実行，入出力テストを行うジョブ．
- `cppcheck-warning` : cppcheckによる静的解析で警告が出た場合にUNSTABLEとするジョブ．
- `cppcheck-error` : cppcheckによる静的解析でエラーが出た場合にビルド失敗とするジョブ．
- `vulkan` : Vulkanコードをビルドし，headless実行して描画結果をartifactに保存するジョブ．

このjobはGitLabのレポジトリから`Jenkinsfile_*`を読み込む．

これにより，Jenkins上でジョブを手動で構築することなく，構築された状態で起動する．

※ジョブ作成の設定を更新した場合は，Jenkinsを再起動する．

```sh
docker compose restart jenkins
```

### GitLabのレポジトリ
ソースコード用の`handmade-cicd`と，イメージ作成用の`cpp-builder-image`を使用する．
各ジョブは`handmade-cicd`からそれぞれのJenkinsfileを読み込む．

GitLabへのPushをWebhookでJenkinsへ通知する．変更されたファイルによって起動するジョブを分ける．

| 変更するファイル | 起動するジョブ |
| --- | --- |
| `src/hello.cpp`，`Jenkinsfile`，`Jenkinsfile_hello` | `cpp-hello` |
| `src/overflow.cpp`，`Jenkinsfile_warning` | `cppcheck-warning` |
| `src/memleak.cpp`，`Jenkinsfile_error` | `cppcheck-error` |
| `src/vulkan.cpp`，`src/shader.slang`，`Jenkinsfile_Vulkan` | `vulkan` |

ビルド用コンテナは`--volumes-from jenkins-test`でJenkinsのボリュームを使用する．作業ディレクトリは`$WORKSPACE`とし，GitLabからcheckoutしたソースをビルドする．

CppCheckを使う場合は，GitLab Container Registryから`localhost:5050/root/cpp-builder-image/cppcheck:latest`をpullして静的解析を行う．cppcheckのイメージはGitLab CIでビルドする．

### Pipeline
`Jenkinsfile`および`Jenkinsfile_*`はJenkins Pipelineの定義ファイル．
中身はGroovyベースのDeclarative Pipeline．

`cpp-hello`の現在の流れ:

1. GitLab Container Registryから`localhost:5050/root/cpp-builder-image/cppcheck:latest`をpullし，`src/hello.cpp`を静的解析する
2. GitLab Container Registryから`localhost:5050/root/cpp-builder-image:latest`をpullする
3. C++ Builderで`src/hello.cpp`を`build/hello`にコンパイルし，実行
4. 出力に`TEST`が含まれることと，入力に3と5を与えたときに`Sum: 8`が含まれることを確認する
5. `build/`を削除する．各ステージで使用したコンテナは実行後に破棄する

実行時に入力待ちで終わらない状態となるのを防ぐため，タイムアウトを設定している．

GitLab CIではC++ビルド用とcppcheck用のイメージを作り，Jenkinsではそれぞれのイメージを使ってビルドと静的解析を行う．
ソースコードは`handmade-cicd`へpushする．イメージの更新は`cpp-builder-image`で行う．

### 静的解析
cppcheckによる静的解析をビルド前に行う．

warningはUNSTABLE，errorはビルド失敗とする．

### Vulkan
GitLab CIでVulkan用イメージを作り，Container Registryへ保存する．`Jenkinsfile_Vulkan`はそのイメージをpullして`src/vulkan.cpp`をコンパイルする．
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
Vulkan SDKがx86_64版なので，GitLab CIでは`linux/amd64`のイメージを作る．Jenkinsでもpullと実行時に`--platform=linux/amd64`を指定する．

## 構築
以下はリポジトリのルートで実行する．

### 1. リポジトリ取得
cppcheckのsubmoduleを含めて取得する．

```sh
git clone --recurse-submodules https://github.com/ITO1103/handmade-cicd.git
cd handmade-cicd
```

既にclone済みの場合は，submoduleを初期化する．
```sh
git submodule update --init --recursive
```

### 2. GitLab起動
別端末からGitLabを開く場合は，`.env.example`を`.env`にコピーし，`GITLAB_HOST`にホスト名またはIPを設定する．同じ端末から使う場合は未設定で良い．

```sh
cp .env.example .env
```

GitLabを起動する．
```sh
docker compose up -d gitlab
```
GitLabの起動には時間がかかる．環境にもよるが5分程度待つ．

管理画面は`http://localhost:8929`で開く．別端末から接続する場合は，`localhost`を設定したホスト名またはIPに置き換える．

初期管理者ユーザーは`root`．初期パスワードは以下で確認できる．

```sh
docker compose exec gitlab grep 'Password:' /etc/gitlab/initial_root_password
```

初期パスワードのファイルは，初回起動から24時間経過後のコンテナ再起動で削除される．確認後はGitLab上でパスワードを変更する．その後のプロジェクト作成画面はスキップして良い．

SSHでGitLabを使う場合は，公開鍵を登録する．公開鍵がない場合は作成する．

```sh
ssh-keygen -t ed25519
cat ~/.ssh/id_ed25519.pub
```

GitLabのユーザーアイコンから`Edit profile` → `Access` → `SSH keys` → `Add new key`を開き，公開鍵を登録する．秘密鍵は登録しない．

### 3. C++ビルド用プロジェクト作成
GitLabに`root`でログインし，`Projects` → `Create a project` → `Create blank project`を開く．

- Project name：`cpp-builder-image`
- Project URL / namespace：`root`
- Project slug：`cpp-builder-image`
- Visibility Level：`Public`
- `Initialize repository with a README`：チェック

`Create project`を押すと`main`ブランチが作成される．

`Settings` → `General`でContainer Registryが有効になっていることを確認する．`Settings` → `Repository`では`main`が保護され，pushできるのがMaintainerだけになっていることを確認する．

### 4. Project Runnerを登録
`Settings` → `CI/CD` → `Runners` → `Create project runner`からProject Runnerを作成する．設定は次のとおり．

- Tag：`docker`
- `Protected`：有効
- `Run untagged jobs`：無効

作成直後の画面に表示されるRunner認証トークン（`glrt-...`）を登録時に使う．

Runnerの詳細画面が404になる場合は，ログイン時と同じホスト名で開いているか確認する．

GitLab Runnerを登録する．`--template-config`でジョブ用コンテナの設定を記した`runner-template.toml`を指定する．
```sh
docker compose --profile gitlab-ci run --rm gitlab-runner register \
  --url http://localhost:8929 \
  --executor docker \
  --docker-image docker:27.5.1-cli \
  --docker-pull-policy if-not-present \
  --template-config /runner-template.toml
```
登録中にトークンを聞かれたら，画面に表示されたものを入力する．それ以外はそのままEnterで進める．

登録後，Runnerを起動．
```sh
docker compose --profile gitlab-ci up -d gitlab-runner
```

GitLabのRunner一覧で，RunnerがOnlineになっていることを確認する．

### 5. GitLab CIでイメージを作る
SSH鍵を登録した端末からGitLabへの接続を確認する．
```sh
ssh -T -p 2424 git@localhost
```

※GitLabを作り直すとSSHのホスト鍵も変わる．その場合に`REMOTE HOST IDENTIFICATION HAS CHANGED`が出たら，古い登録を削除して再接続する．
```sh
ssh-keygen -R '[localhost]:2424'
ssh -T -p 2424 git@localhost
```
別端末から接続している場合は，`localhost`を使用しているホスト名またはIPに置き換える．

`cpp-builder-image`をcloneし，このリポジトリに置いた設定ファイルをコピーする．今回は`handmade-cicd`の一つ上のディレクトリにcloneする．
別端末から接続する場合は，`localhost`をGitLabのホスト名またはIPに置き換える．
```sh
git clone ssh://git@localhost:2424/root/cpp-builder-image.git ../cpp-builder-image
cp -a gitlab/projects/cpp-builder-image/. ../cpp-builder-image/
```

コピーしたファイルをcommitしてpushする．

```sh
git -C ../cpp-builder-image add -A
git -C ../cpp-builder-image commit -m "Add C++ builder image"
git -C ../cpp-builder-image push origin main
```

GitLabの`Build` → `Pipelines`でパイプラインの結果を確認する．成功するとContainer Registryにイメージが保存される．各イメージにはコミットごとの短縮SHAをtagとして付ける．mainへのpushでは`latest`も更新し，Jenkinsはこの`latest`を使う．cppcheck用とVulkan用のイメージは，それぞれのDockerfileか`.gitlab-ci.yml`を変更した場合に作る．

`.gitlab-ci.yml`とDockerfileは`gitlab/projects/cpp-builder-image`に置いてある．cppcheck用とVulkan用のDockerfileはそれぞれ`gitlab/projects/cpp-builder-image/cppcheck/Dockerfile`，`gitlab/projects/cpp-builder-image/vulkan/Dockerfile`に置く．GitLab側のプロジェクトで変更する場合は，コピーした先で編集する．

### 6. ソースコード用のプロジェクトを作成する
GitLabに`root`でログインし，`Projects` → `Create a project` → `Create blank project`を開く．

- Project name：`handmade-cicd`
- Project URL / namespace：`root`
- Project slug：`handmade-cicd`
- Visibility Level：`Public`
- `Initialize repository with a README`：チェックしない

イメージ用の`cpp-builder-image`とは別のプロジェクト．C++，VulkanのソースコードとJenkinsfileは全てここに置く．

GitLab用のremoteを追加する．GitHub用の`origin`は変更しない．
```sh
git remote add gitlab ssh://git@localhost:2424/root/handmade-cicd.git
```
既に`gitlab`を追加している場合は，`git remote get-url gitlab`で接続先を確認する．変更する場合は以下．
```sh
git remote set-url gitlab ssh://git@localhost:2424/root/handmade-cicd.git
```

pushには手順2で登録したSSH鍵を使用する．

まずは現在のコミットをGitLabの`main`へpushする．未コミットの変更は反映されないため，Jenkinsfile等を変更している場合は先にコミットする．
```sh
git push -u gitlab HEAD:main
```

Jenkinsからは`http://gitlab:8929/root/handmade-cicd.git`でソースを取得する．Publicにしているため，取得用の認証情報は不要．

namespaceやプロジェクト名を変える場合は，`.env`に`GITLAB_REPO_URL`を設定する．コンテナ間の接続なので，ホスト名は`gitlab`を使用する．

### 7. Jenkinsを起動する

GitLab CIでContainer RegistryにイメージができてからJenkinsを起動する．

```sh
docker compose up -d --build jenkins
```

Jenkinsの管理画面URL:

```text
http://localhost:8080
```

起動後，`cpp-hello`，`cppcheck-warning`，`cppcheck-error`，`vulkan`という名前のジョブが作成されているので，再生ボタンを押してジョブを実行．

`cpp-hello`はジョブの詳細画面から`Console Output`を確認し，イメージのpullとC++のビルド，入出力テストが成功しているかを確認．
`vulkan`はビルド結果画面の`Build Artifacts`から`artifacts/vulkan/vulkan.png`を開いて三角形が描画されているかを確認．

イメージ用の`cpp-builder-image`はPublicにしているため，Jenkins側ではRegistryへのログインを設定していない．

`cppcheck-warning`はUNSTABLE，`cppcheck-error`はFAILUREになることを確認する．静的解析でwarning，errorが出るサンプルを使っているため，この結果で良い．

`.env`を変更した場合は`docker compose up -d --build jenkins`でコンテナを作り直す．ジョブ作成のGroovyだけを更新した場合は`docker compose restart jenkins`で良い．

### 8. Pushでジョブを起動する
Jenkinsの`管理` → `Security` → `Git plugin notifyCommit access tokens`でJenkinsへPushを通知するための通知用トークンを作成する．

GitLabの`Admin` → `Settings` → `Network` → `Outbound requests`で，`Local IP addresses and domain names that hooks and integrations can access`に`jenkins:8080`を追加して保存．

`handmade-cicd`の`Settings` → `Webhooks`で以下を設定する．

- URL：`http://jenkins:8080/git/notifyCommit?url=http%3A%2F%2Fgitlab%3A8929%2Froot%2Fhandmade-cicd.git&token=(Jenkinsの通知用トークン)`
- Trigger：`Push events`

※`GITLAB_REPO_URL`を変更した場合は，Webhookの`url`も同じURLをURLエンコードして指定する．通知先のホスト名は`jenkins`．

保存後，`Test` → `Push events`でHTTP 200になることを確認する．

変更の確認には前回のcheckoutを使うので，各ジョブは手順7で一度実行しておく．Poll SCMはGroovyで設定する．スケジュールは空とし，Webhookを受けた時だけ変更を確認する．

例えば`src/hello.cpp`を変更して，GitLabへpushする．
```sh
git add src/hello.cpp
git commit -m "コメント"
git push gitlab HEAD:main
```
Jenkinsで`cpp-hello`が起動することを確認する．Vulkanの場合は`src/vulkan.cpp`または`src/shader.slang`を変更する．READMEだけの変更ではジョブは起動しない．

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
- GitLab CIでコンテナイメージをビルドしてContainer Registryへ保存する:
  - https://docs.gitlab.com/user/packages/container_registry/build_and_push_images/
- Project Runnerを作成する:
  - https://docs.gitlab.com/ci/runners/runners_scope/
- GitLab RunnerのDocker executor:
  - https://docs.gitlab.com/runner/executors/docker/
- 静的解析ツール cppcheckの導入，使用方法:
  - https://kinoshita-hidetoshi.github.io/Programing-Items/C++/etc/cppcheck.html
- cppcheckおよびMISRA C++のDocker:
  - https://github.com/Facthunder/cppcheck.git
- Vulkanのドキュメント(環境構築)
  - https://docs.vulkan.org/tutorial/latest/02_Development_environment.html#_linux

- Gitプラグインの変更通知とPoll SCM：
  - https://plugins.jenkins.io/git/#push-notification-from-repository
- GitLabのWebhook接続先の許可：
  - https://docs.gitlab.com/security/webhooks/
