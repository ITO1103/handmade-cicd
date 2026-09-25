# C++ Builder用コンテナ

このプロジェクトでは，`.gitlab-ci.yml`がDockerfileからイメージを作り，Container Registryに保存する．

- コミットごとに短縮SHAをタグとして付ける．
- default branchへのpushでは`latest`タグも更新する．
- イメージのベースには`builder/cpp/Dockerfile`と同じ`gcc:latest`を使う．

`.gitlab-ci.yml`を実行するには`docker`タグを付けたProject Runnerが必要．ローカルRunnerはホストのDocker socketとhost networkingを使い，ジョブから`localhost:8929`と`localhost:5050`へ接続する．ホストのDocker daemonを操作できるため，Runnerは保護ブランチのジョブだけを実行し，このリポジトリ専用で使う．
