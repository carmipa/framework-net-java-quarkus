#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
COMPOSE_SERVICE="${COMPOSE_SERVICE:-framework-net}"
ENV_FILE="${ENV_FILE:-$APP_DIR/.env}"
DEFAULT_HTTP_PORT="${HTTP_PORT:-18080}"
SHOW_ADMIN_KEY="${SHOW_ADMIN_KEY:-0}"

for arg in "$@"; do
  case "$arg" in
    --show-admin-key)
      SHOW_ADMIN_KEY=1
      ;;
    *)
      echo "Uso: $0 [--show-admin-key]" >&2
      exit 2
      ;;
  esac
done

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Comando obrigatório não encontrado: $1" >&2
    exit 1
  fi
}

get_env_value() {
  local key="$1"
  grep -E "^${key}=" "$ENV_FILE" | tail -n 1 | cut -d= -f2-
}

replace_or_append_env() {
  local key="$1"
  local value="$2"

  if grep -qE "^${key}=" "$ENV_FILE"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$ENV_FILE"
  else
    printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
}

ensure_env_file() {
  local created=0

  if [ ! -f "$ENV_FILE" ]; then
    cat > "$ENV_FILE" <<EOF
HTTP_PORT=${DEFAULT_HTTP_PORT}
ADMIN_API_KEY=$(openssl rand -hex 32)
CSRF_SECRET=$(openssl rand -hex 32)
EOF
    chmod 600 "$ENV_FILE" || true
    created=1
  fi

  if ! grep -qE '^HTTP_PORT=' "$ENV_FILE"; then
    replace_or_append_env "HTTP_PORT" "$DEFAULT_HTTP_PORT"
  fi

  if ! grep -qE '^ADMIN_API_KEY=' "$ENV_FILE" || grep -qE '^ADMIN_API_KEY=troque-por-um-valor' "$ENV_FILE"; then
    replace_or_append_env "ADMIN_API_KEY" "$(openssl rand -hex 32)"
    created=1
  fi

  if ! grep -qE '^CSRF_SECRET=' "$ENV_FILE" || grep -qE '^CSRF_SECRET=troque-por-um-valor' "$ENV_FILE"; then
    replace_or_append_env "CSRF_SECRET" "$(openssl rand -hex 32)"
    created=1
  fi

  if [ "$created" -eq 1 ]; then
    SHOW_ADMIN_KEY=1
  fi
}

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

wait_for_health() {
  local container_id
  local status
  local deadline

  container_id="$(compose ps -q "$COMPOSE_SERVICE")"
  if [ -z "$container_id" ]; then
    echo "Container do serviço $COMPOSE_SERVICE não encontrado." >&2
    compose ps
    exit 1
  fi

  deadline=$((SECONDS + 150))
  while [ "$SECONDS" -lt "$deadline" ]; do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)"

    case "$status" in
      healthy|running)
        echo "Healthcheck OK: $status"
        return 0
        ;;
      unhealthy|exited|dead)
        echo "Container ficou em estado $status. Logs recentes:" >&2
        compose logs --tail=80 "$COMPOSE_SERVICE" >&2
        exit 1
        ;;
      *)
        sleep 3
        ;;
    esac
  done

  echo "Timeout esperando healthcheck. Estado atual: ${status:-desconhecido}" >&2
  compose ps >&2
  compose logs --tail=80 "$COMPOSE_SERVICE" >&2
  exit 1
}

require_command git
require_command docker
require_command openssl

cd "$APP_DIR"
ensure_env_file

echo "Atualizando código em $APP_DIR"
git pull origin main

echo "Subindo container com Docker Compose"
compose up -d --build
compose ps
wait_for_health

echo "Logs recentes:"
compose logs --tail=20 "$COMPOSE_SERVICE"

echo
echo "Deploy concluído."
echo "Porta local: 127.0.0.1:$(get_env_value HTTP_PORT)"
if [ "$SHOW_ADMIN_KEY" = "1" ]; then
  echo "ADMIN_API_KEY: $(get_env_value ADMIN_API_KEY)"
else
  echo "ADMIN_API_KEY já existe em $ENV_FILE. Use --show-admin-key se precisar exibir."
fi
