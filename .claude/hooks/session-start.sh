#!/bin/bash
set -euo pipefail

# Only run in remote Claude Code sessions
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

echo "Syncing with origin/main..."
git fetch origin main
git checkout main 2>/dev/null || true
git pull origin main
echo "Sync complete. Now on branch: $(git branch --show-current), commit: $(git rev-parse --short HEAD)"
