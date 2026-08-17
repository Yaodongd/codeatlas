#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_PATH="${1:-/www/wwwroot/codeatlas}"
BRANCH="${DEPLOY_BRANCH:-master}"

cd "$DEPLOY_PATH"
test -d .git || { echo "ERROR: 目标目录不是 Git 仓库"; exit 1; }
test -f .env || { echo "ERROR: 缺少生产环境 .env"; exit 1; }

if [[ -n "$(git status --porcelain --untracked-files=normal)" ]]; then
  echo "ERROR: 服务器仓库存在未提交改动"
  git status --short
  exit 1
fi

PREVIOUS_SHA="$(git rev-parse HEAD)"
git fetch --prune origin "$BRANCH"
git merge-base --is-ancestor HEAD "origin/$BRANCH" || {
  echo "ERROR: 服务器分支与远程分支已分叉"
  exit 1
}
git merge --ff-only "origin/$BRANCH"

docker compose config --quiet
docker compose build

mkdir -p backups
if docker compose ps --status running --services | grep -qx postgres; then
  BACKUP="backups/codeatlas-$(date +%Y%m%d-%H%M%S)-${PREVIOUS_SHA:0:8}.sql"
  docker compose exec -T postgres sh -c \
    'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$BACKUP"
  echo "数据库备份完成: $BACKUP"
fi

docker compose up -d --remove-orphans
APP_PORT="$(sed -n 's/^APP_PORT=//p' .env | tail -n 1)"
APP_PORT="${APP_PORT:-18081}"

for attempt in {1..30}; do
  if curl -fsS "http://127.0.0.1:${APP_PORT}/codeatlas/actuator/health/readiness" >/dev/null; then
    echo "部署成功: $(git rev-parse HEAD)"
    docker compose ps
    exit 0
  fi
  echo "等待健康检查 (${attempt}/30)"
  sleep 4
done

echo "ERROR: 部署后健康检查失败"
docker compose ps
docker compose logs --tail=120 backend frontend gateway
exit 1

