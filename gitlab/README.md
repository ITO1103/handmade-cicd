# GitLab

GitLabサービスで使用するディレクトリ．

```text
gitlab
  - config : GitLabの設定
  - logs   : GitLabのログ
  - data   : GitLabのデータ
```

GitLabの起動時に生成・更新されるため，Git管理対象外としている．

Web UIは`http://localhost:8929`．Git over SSHはホストの2424番ポートを使用する．

初期管理者ユーザーは`root`．初期パスワードは以下で確認できる．
```sh
docker compose exec gitlab grep 'Password:' /etc/gitlab/initial_root_password
```

初期パスワードのファイルは，初回起動から24時間経過後のコンテナ再起動で削除される．確認後はGitLab上でパスワードを変更する．

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

接続を確認する．
```sh
ssh -T -p 2424 git@localhost
```

cloneする．
```sh
git clone ssh://git@localhost:2424/iisec/test.git
```
