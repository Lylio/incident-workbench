#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
command -v docker >/dev/null || { echo "Install Docker Engine/Desktop first."; exit 1; }
docker compose version >/dev/null
docker info >/dev/null 2>&1 || { echo "Start Docker and check your user's Docker access."; exit 1; }
if [ ! -f .env ]; then
  command -v python3 >/dev/null || { echo "Install Python 3, or copy .env.example to .env and choose available ports."; exit 1; }
  python3 scripts/select_ports.py
fi
docker compose up --build -d
workbench_address="$(docker compose port frontend 80)"
echo ""
echo "Workbench: http://${workbench_address}"
echo "Wait for the backend to start, then click Explore sample incident."
echo "AI is optional: configure OPENAI_API_KEY in .env to enable real model analysis."
