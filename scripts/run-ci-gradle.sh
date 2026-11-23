#!/usr/bin/env bash
# Print useful diagnostics as an Actions annotation when a Gradle build fails.
set -euo pipefail
log="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/netoptimizer-gradle.log"
if gradle --no-daemon --console plain "$@" > "$log" 2>&1; then
  cat "$log"
else
  tail -120 "$log"
  python3 - "$log" <<'PY'
from pathlib import Path
import re
import sys
s = Path(sys.argv[1]).read_text(errors="replace")
lines = s.splitlines()
important = [line for line in lines if re.search(r'(^e:| error:|error\[|What went wrong|failed|^\* )', line, re.I)]
text = ('\n'.join(important[-35:]) + '\n' + '\n'.join(lines[-35:]))[-6500:]
print('::error::' + text.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))
PY
  exit 1
fi
