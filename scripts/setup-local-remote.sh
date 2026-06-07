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

# Jenkins の初期ジョブが参照するファイルが local remote に存在するか確認する
required_paths=(
  Jenkinsfile_hello
  Jenkinsfile_warning
  Jenkinsfile_error
  Jenkinsfile_Vulkan
  src/hello.cpp
  src/memleak.cpp
  src/overflow.cpp
  src/shader.slang
  src/vulkan.cpp
)

missing_paths=()
for path in "${required_paths[@]}"; do
  if ! git -C "$remote_dir" cat-file -e "refs/heads/main:${path}" 2>/dev/null; then
    missing_paths+=("$path")
  fi
done

if ((${#missing_paths[@]} > 0)); then
  echo "Warning: local remote main does not contain files used by Jenkins jobs:" >&2
  printf '  - %s\n' "${missing_paths[@]}" >&2
  echo "Commit the missing files, then run this script again." >&2
fi

# Jenkins のビルドトリガー用の post-receive hook を配置
cat > "$hook_file" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

# Jenkins のビルドトリガー用の認証トークンを環境変数から読み込む
repo_root="__REMOTE_DIR__"
trigger_token="__TRIGGER_TOKEN__"

trigger_job() {
  local job_name="$1"
  curl -fsS "http://localhost:8080/job/${job_name}/build?delay=0&token=${trigger_token}" >/dev/null
}

while read -r oldrev newrev refname; do
  if [[ "$refname" != "refs/heads/main" ]]; then
    continue
  fi

  if [[ "$oldrev" =~ ^0+$ ]]; then
    changed_files="$(git -C "$repo_root" ls-tree -r --name-only "$newrev")"
  else
    changed_files="$(git -C "$repo_root" diff --name-only "$oldrev" "$newrev")"
  fi

  trigger_cpp_hello=false
  trigger_cppcheck_warning=false
  trigger_cppcheck_error=false
  trigger_vulkan=false

  while IFS= read -r path; do
    case "$path" in
      src/hello.cpp|Jenkinsfile|Jenkinsfile_hello)
        trigger_cpp_hello=true
        ;;
      src/overflow.cpp|Jenkinsfile_warning)
        trigger_cppcheck_warning=true
        ;;
      src/memleak.cpp|Jenkinsfile_error)
        trigger_cppcheck_error=true
        ;;
      src/vulkan.cpp|src/shader.slang|Jenkinsfile_Vulkan)
        trigger_vulkan=true
        ;;
    esac
  done <<< "$changed_files"

  if [[ "$trigger_cpp_hello" == true ]]; then
    trigger_job cpp-hello
  fi

  if [[ "$trigger_cppcheck_warning" == true ]]; then
    trigger_job cppcheck-warning
  fi

  if [[ "$trigger_cppcheck_error" == true ]]; then
    trigger_job cppcheck-error
  fi

  if [[ "$trigger_vulkan" == true ]]; then
    trigger_job vulkan
  fi
done
EOF

if sed --version >/dev/null 2>&1; then
  sed -i \
    -e "s|__REMOTE_DIR__|$remote_dir|g" \
    -e "s|__TRIGGER_TOKEN__|$trigger_token|g" \
    "$hook_file"
else
  sed -i '' \
    -e "s|__REMOTE_DIR__|$remote_dir|g" \
    -e "s|__TRIGGER_TOKEN__|$trigger_token|g" \
    "$hook_file"
fi
chmod +x "$hook_file"

# デバッグ用ログ
echo "Local remote ready: $remote_url"
echo "Push future changes with: git push local main"
echo "Jenkins trigger token stored at: $trigger_token_file"
echo "If Jenkins is already running, restart it so init.groovy.d can create/update jobs."
