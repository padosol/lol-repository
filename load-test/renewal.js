// 단건 갱신 측정: 소환사 한 명을 한 번 갱신 요청 → 상태 조회(0.5초마다, 최대 5초). 여러 건은 renewal-steady.js.
// 대상은 -e PUUID=... 로 준 소환사, 없으면 setup.sh 가 준비한 targets.json 의 첫 소환사다.
//
//   bash load-test/setup.sh                       # 먼저 한 번: 대상 소환사를 DB 에 등록하고 targets.json 을 쓴다
//   k6 run load-test/renewal.js
//   k6 run -e PUUID=<puuid> load-test/renewal.js  # 다른 소환사 (DB 에 있어야 한다)
//
// 앱은 local 프로파일(공개 포트 8111)로 실제 RIOT_API_KEY 를 넣어 띄워 둔다. 다른 주소면 -e BASE_URL=... 로 바꾼다.
//
// 같은 소환사는 갱신 뒤 2분, 클릭 뒤 10초 동안 다시 갱신되지 않는다(200 + FAILED 로 돌아온다).
// 여기서 갱신한 소환사는 첫 갱신이 아니게 된다. renewal-steady.js 를 첫 갱신 조건으로 돌리려면 그 전에 reset-db.sh 를 돌린다.
import { check } from 'k6';
import { SUCCESS_SECONDS, renew } from './lib/renewal.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8111';
const TARGETS_FILE = __ENV.TARGETS || './targets.json';

function firstTarget() {
  let raw;
  try {
    raw = open(TARGETS_FILE);
  } catch (e) {
    throw new Error(`${TARGETS_FILE} 가 없습니다. 먼저 bash load-test/setup.sh 를 돌리거나 -e PUUID=... 를 주세요.`);
  }
  const puuid = (JSON.parse(raw).puuids || [])[0];
  if (!puuid) {
    throw new Error(`${TARGETS_FILE} 에 소환사가 없습니다. bash load-test/setup.sh 를 다시 돌리세요.`);
  }
  return puuid;
}

const PUUID = __ENV.PUUID || firstTarget();

export const options = {
  scenarios: {
    renewal: { executor: 'shared-iterations', vus: 1, iterations: 1, maxDuration: '1m' },
  },
};

export default function () {
  const { status, seconds } = renew(BASE, PUUID);
  if (status === 'COOLDOWN') {
    console.log(`${PUUID.slice(0, 12)}… 쿨다운이라 갱신하지 않음 (갱신 뒤 2분, 클릭 뒤 10초)`);
    return;
  }
  check(status, { '갱신 SUCCESS': (s) => s === 'SUCCESS' });
  check(seconds, { [`갱신 ${SUCCESS_SECONDS}초 이내`]: (s) => status === 'SUCCESS' && s <= SUCCESS_SECONDS });
  console.log(`${PUUID.slice(0, 12)}… 갱신 ${status} (${seconds.toFixed(1)}초)`);
}
