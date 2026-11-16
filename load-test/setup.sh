#!/usr/bin/env bash
# 부하 대상 소환사를 준비한다. 데이터셋(data.js)의 소환사를 앱 DB 에서 찾고, 없으면 Riot 에서 puuid 로 등록한다.
# 등록까지 된 소환사의 puuid 를 targets.json 에 적는다. renewal-steady.js 는 이 파일만 읽는다.
#
#   bash load-test/setup.sh                          # load-test/data.js → load-test/targets.json
#   DATA=/절대/경로/other.js bash load-test/setup.sh # 다른 데이터셋
#
# 앱이 떠 있어야 한다. 처음 등록하는 소환사는 Riot 을 부르므로(계정·소환사·리그) 300명이면 몇 분 걸린다.
# 등록이 안 되는 소환사(Riot 응답 오류, 앱 500 등)는 상태 코드와 함께 보여 주고 뺀다.
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
DATA="${DATA:-${DIR}/data.js}"
TARGETS="${TARGETS:-${DIR}/targets.json}"
BASE_URL="${BASE_URL:-http://localhost:8111}"
PLATFORM="${PLATFORM:-kr}"

if [ ! -f "$DATA" ]; then
  echo "데이터셋이 없습니다: ${DATA} (형식은 load-test/README.md 의 \"데이터셋\")" >&2
  exit 1
fi
if ! curl -fs -m 3 -o /dev/null "${BASE_URL}/actuator/health"; then
  echo "앱이 ${BASE_URL} 에 떠 있지 않습니다. 앱을 먼저 띄우세요 (./gradlew :module:app:application:bootRun)." >&2
  exit 1
fi

DATA="$DATA" TARGETS="$TARGETS" BASE_URL="$BASE_URL" PLATFORM="$PLATFORM" python3 -I - <<'PY'
import json
import os
import time
import urllib.error
import urllib.request

data_path, targets_path = os.environ["DATA"], os.environ["TARGETS"]
base, platform = os.environ["BASE_URL"].rstrip("/"), os.environ["PLATFORM"]

# data.js 는 `const userInfo = { ..., "entries": [{ "puuid": ... }] }` 형식이다. 첫 { 부터 마지막 } 까지를 JSON 으로 읽는다.
raw = open(data_path, encoding="utf-8").read()
puuids = [e["puuid"] for e in json.loads(raw[raw.index("{"): raw.rindex("}") + 1])["entries"]]


def status_of(url):
    try:
        with urllib.request.urlopen(url, timeout=30) as res:
            return res.status
    except urllib.error.HTTPError as e:
        return e.code
    except (urllib.error.URLError, TimeoutError) as e:
        return f"오류({getattr(e, 'reason', e)})"


usable, registered, dropped = [], 0, []
started = time.time()
for i, puuid in enumerate(puuids, 1):
    if status_of(f"{base}/api/v1/summoners/{puuid}") == 200:
        usable.append(puuid)
    else:
        status = status_of(f"{base}/api/riot/{platform}/summoners/{puuid}")
        if status == 200:
            usable.append(puuid)
            registered += 1
        else:
            dropped.append((puuid, status))
    if i % 50 == 0 or i == len(puuids):
        print(f"  {i}/{len(puuids)} 확인 (등록 {registered}, 제외 {len(dropped)}, {time.time() - started:.0f}초)", flush=True)

for puuid, status in dropped:
    print(f"  제외 {puuid[:12]}… ({status})")
print(f"대상 {len(usable)}/{len(puuids)}명 (이미 있음 {len(usable) - registered}, 새로 등록 {registered}, 제외 {len(dropped)})")

if not usable:
    raise SystemExit("갱신할 소환사가 없습니다. targets.json 을 쓰지 않았습니다.")
with open(targets_path, "w", encoding="utf-8") as f:
    json.dump({
        "createdAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "source": os.path.basename(data_path),
        "baseUrl": base,
        "puuids": usable,
    }, f, ensure_ascii=False, indent=2)
    f.write("\n")
print(f"썼습니다: {targets_path}")
PY
