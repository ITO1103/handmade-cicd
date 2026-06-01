#!/usr/bin/env bash
set -euo pipefail # エラーハンドリングと未定義変数の使用禁止

# ローカルの bare Git リポジトリをセットアップし、Jenkins のビルドトリガー用の認証トークンを生成
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
remote_dir="$repo_root/.local/git/handmade-cicd.git"
remote_url="file://$remote_dir"
trigger_token_file="$repo_root/.local/jenkins-trigger-token"
hook_file="$remote_dir/hooks/post-receive"

mkdir -p "$(dirname "$remote_dir")"

# Jenkins のビルドトリガー用の認証トークンを生成して保存 (既に存在する場合は再利用)
if [[ ! -f "$trigger_token_file" ]]; then
  mkdir -p "$(dirname "$trigger_token_file")"
  token_source="$(date +%s)-$(git -C "$repo_root" rev-parse --short HEAD 2>/dev/null || echo nohead)"
  printf '%s' "local-${token_source}" > "$trigger_token_file"
fi

trigger_token="$(cat "$trigger_token_file")"

# ローカルの bare Git リポジトリをセットアップし、Jenkins のビルドトリガー用の post-receive hook を配置
if [[ ! -d "$remote_dir" ]]; then
  git -C "$repo_root" init --bare "$remote_dir"
fi

# local remote を追加または更新
if git -C "$repo_root" remote get-url local >/dev/null 2>&1; then
  git -C "$repo_root" remote set-url local "$remote_url"
else
  git -C "$repo_root" remote add local "$remote_url"
fi

# 既存のコミットを local remote にミラーリング
git -C "$repo_root" push --mirror local
git -C "$remote_dir" symbolic-ref HEAD refs/heads/main || true

# Jenkins のビルドトリガー用の post-receive hook を配置
cat > "$hook_file" <<EOF
#!/usr/bin/env bash
set -euo pipefail

# Jenkins のビルドトリガー用の認証トークンを環境変数から読み込む
repo_root="$repo_root"
trigger_token="$trigger_token"

# main ブランチへの push のみをトリガーとする
while read -r oldrev newrev refname; do
  if [[ "\$refname" != "refs/heads/main" ]]; then
    continue
  fi

  curl -fsS "http://localhost:8080/job/cpp-hello/build?delay=0&token=${trigger_token}" >/dev/null
done
EOF
chmod +x "$hook_file"

# デバッグ用ログ
echo "Local remote ready: $remote_url"
echo "Push future changes with: git push local main"
echo "Jenkins trigger token stored at: $trigger_token_file"