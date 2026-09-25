# GitLab

GitLabサービスで使用するディレクトリ．

```text
gitlab
  - config : GitLabの設定
  - logs   : GitLabのログ
  - data   : GitLabのデータ
```

GitLabの起動時に生成・更新されるため，Git管理対象外としている．

Web UIは`http://localhost:8929`．Git over SSHはホストの2424番ポートを使用する．Container Registryは`http://localhost:5050`で公開する．

GitLabを初期状態へ戻す場合は，コンテナに加えて`config`，`logs`，`data`の中身も削除する．

初期管理者ユーザーは`root`．初期パスワードは以下で確認する．

```sh
docker compose exec gitlab grep 'Password:' /etc/gitlab/initial_root_password
```

初期パスワードのファイルは，初回起動から24時間経過後のコンテナ再起動で削除される．確認後はGitLab上でパスワードを変更する．

## 参考

- [GitLab CIでコンテナイメージをビルドしてContainer Registryへ保存する](https://docs.gitlab.com/user/packages/container_registry/build_and_push_images/)
- [GitLabでプロジェクトを作成する](https://docs.gitlab.com/user/project/)
- [Project Runnerを作成する](https://docs.gitlab.com/ci/runners/runners_scope/)
- [GitLab RunnerのDocker executor](https://docs.gitlab.com/runner/executors/docker/)
