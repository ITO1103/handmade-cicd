# ビルド環境イメージ

このプロジェクトでは，`.gitlab-ci.yml`がC++ビルド用とcppcheck用のイメージを作り，Container Registryに保存する．

| 用途 | Dockerfile | イメージ名 |
| --- | --- | --- |
| C++ビルド | `Dockerfile` | `localhost:5050/root/cpp-builder-image` |
| cppcheck | `cppcheck/Dockerfile` | `localhost:5050/root/cpp-builder-image/cppcheck` |

各イメージにはコミットごとに短縮SHAのタグを付ける．mainへのpushでは`latest`タグも更新する．cppcheck用イメージは`cppcheck/Dockerfile`か`.gitlab-ci.yml`を変更した場合に作る．

C++ビルド用イメージのベースには`builder/cpp/Dockerfile`と同じ`gcc:latest`を使う．cppcheck用イメージは，リポジトリルートの`cppcheck/Dockerfile`をこのプロジェクトの`cppcheck/Dockerfile`にも反映してビルドする．

`.gitlab-ci.yml`を実行するには`docker`タグを付けたProject Runnerが必要．ローカルRunnerはホストのDocker socketとhost networkingを使い，ジョブから`localhost:8929`と`localhost:5050`へ接続する．ホストのDocker daemonを操作できるため，Runnerは保護ブランチのジョブだけを実行し，このリポジトリ専用で使う．

RegistryはHTTPで公開している．ホストのDocker daemonでは`localhost:5050`だけがHTTP接続の対象なので，CIのログイン先とpush先にも`localhost:5050`を使う．GitLabが表示する`CI_REGISTRY`がホストのIPアドレスの場合，そのままDockerで使うとHTTPS接続になって失敗する．

Jenkinsは`localhost:5050/root/cpp-builder-image:latest`をC++ビルドに，`localhost:5050/root/cpp-builder-image/cppcheck:latest`を静的解析に使う．
