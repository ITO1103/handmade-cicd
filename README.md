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

## 完了
- Jenkinsの構築
- GroovyによるJenkinsジョブの構築
- ローカルGit Pushによるビルドの自動化
- 簡単なC++コードのビルド
- 出力の簡単なテスト
- 簡単な入力に対する出力テスト

## 構成
可能な限り再現性を保つため，コンテナ上で動作するようにする．

まずは経験のあるLinuxコンテナで構築する．

>※現状macOSもしくはLinuxでしか動作しません！  
bareレポジトリをローカルに作成し，それに対するPushによってJenkinsのビルドがトリガーされるようにするが，bareレポジトリの作成シェルスクリプトはmacOSもしくはLinux用である．  
Windows(PowerShell)は今後対応予定．

### コンテナ
2つのコンテナで構成される．

#### Jenkins本体
```
Jenkins
  - Jenkins controller
  - Docker CLI
  - Jenkins jobの作成
  - C++ builderの起動
```

#### C++コードのビルド，実行用環境
```
C++ builder
  - gcc:latest
  - g++
  - /workspaceにマウントされたsrc/hello.cppをコンパイル，実行
  - コンパイル，実行後はコンテナごと破棄
```

環境汚染防止の観点から，Jenkinsのコンテナ自身ではビルドせず，`gcc:latest`コンテナが別で実行する．


### Job
`jenkins/init.groovy.d/create-cpp-job.groovy`がJenkins起動時に `cpp-hello`というC++をコンパイルするだけのjobを作成する．
このjobはローカルのbareレポジトリからレポジトリルートの`Jenkinsfile`を読み込む．そのため，JenkinsfileをbareレポジトリにPushする必要があるので注意．

これにより，Jenkins上でジョブを手動で構築することなく，構築された状態で起動する．

※起動時に実行されるため，更新後はコンテナの再起動が必要．
```sh
docker compose restart jenkins
```

### ローカルremote
GitHubにはpushせず，レポジトリ内のローカルbareレポジトリをリモートとして使う．

初回にローカルリモートレポジトリを作成し，現在のブランチをmirror pushする．

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

ローカルリモートの`post-receive`hookがJenkinsの`cpp-hello`を直接起動するので，pushした直後にビルドが走る．
ローカルbareレポジトリからのcheckoutを許可するため，Jenkinsコンテナに`JAVA_TOOL_OPTIONS`で`hudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true` を設定する必要がある．

Jenkinsfileを更新した場合は，`git add Jenkinsfile`，`git commit -m "コメント"`，`git push local main`をする．

一方で，`scripts/setup-local-remote.sh`や`jenkins/init.groovy.d/create-cpp-job.groovy`を更新した場合は，`bash scripts/setup-local-remote.sh`を再実行し，必要ならJenkinsを再起動する．(未検証)

CppCheckを使う場合は，`cppcheck/cppcheck/Dockerfile`から`cppcheck:test0`をビルドしてから静的解析を行う．Jenkinsfileではビルドを先に行うようにしている．

### Pipeline
`Jenkinsfile`はJenkins Pipelineの定義ファイル．
中身はGroovyベースのDeclarative Pipeline．

現在の流れ:

1. `cppcheck:test0`を`cppcheck/cppcheck/Dockerfile`からビルドし，`src/hello.cpp`を静的解析する
2. `cpp-builder:test0`を`builder/cpp/Dockerfile`からビルドする
3. C++ Builderで`src/hello.cpp`を`build/hello`にコンパイルし，実行
4. コンパイル結果を実行後，`build/`を削除し，コンテナを破棄する
5. ローカルリモートへのpushをJenkinsが検知して再実行する

ソースが入力を必要とする場合，無限に終わらない状態となるので，タイムアウトを設定している．

## 起動
コンテナのビルドと起動:
```sh
docker compose up -d --build
```

Jenkinsの管理画面URL:
```
http://localhost:8080
```

※Jenkinsの初期パスワードはランダムに生成されるので，以下のコマンドにて確認する．
```sh
docker compose exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

起動し管理者アカウントを作成後，`cpp-hello`という名前のジョブが作成されているので，緑色の再生ボタンを押してジョブを実行．

※ローカルリモートをまだ作っていなければ，先に以下を実行してからJenkinsを再起動．
```sh
bash scripts/setup-local-remote.sh
docker compose restart jenkins
```

ジョブの詳細画面から，`Console Output`を確認し，`Hello, world!`と表示されているかを確認．

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
- 静的解析ツール cppcheckの導入，使用方法:
  - https://kinoshita-hidetoshi.github.io/Programing-Items/C++/etc/cppcheck.html
- cppcheckおよびMISRA C++のDocker:
  - https://github.com/Facthunder/cppcheck.git