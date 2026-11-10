// 실제 Riot API 갱신 확인용 k6 스크립트: 소환사 몇 명을 한 번씩 갱신 요청 → 상태 조회(0.5초마다, 최대 5초) → 매치 목록 확인.
// 대상은 setup.sh 가 준비한 targets.json 의 앞에서부터 COUNT(기본 1)명이다.
//
//   bash load-test/setup.sh                # 먼저 한 번: 대상 소환사를 DB 에 등록하고 targets.json 을 쓴다
//   k6 run load-test/renewal.js
//   k6 run -e COUNT=3 load-test/renewal.js
//
// 앱은 local 프로파일(공개 포트 8111)로 실제 RIOT_API_KEY 를 넣어 띄워 둔다. 다른 주소면 -e BASE_URL=... 로 바꾼다.
//
// 같은 소환사는 갱신 뒤 2분, 클릭 뒤 10초 동안 다시 갱신되지 않는다(200 + FAILED 로 돌아온다).
// 그래서 소환사마다 한 번씩만 돈다.
// 여기서 갱신한 소환사는 첫 갱신이 아니게 된다. renewal-steady.js 를 첫 갱신 조건으로 돌리려면 그 전에 reset-db.sh 를 돌린다.
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { Trend, Counter } from 'k6/metrics';
import { SUCCESS_SECONDS, renew } from './lib/renewal.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8111';
const COUNT = Number(__ENV.COUNT || 1);

if (!(Number.isInteger(COUNT) && COUNT > 0)) {
  throw new Error(`COUNT 는 양의 정수여야 합니다: ${__ENV.COUNT}`);
}

// setup.sh 가 쓴 대상 목록 ({ puuids: [...] }) 의 앞 COUNT 명. 다른 파일은 -e TARGETS=/절대/경로 로 준다.
const TARGETS_FILE = __ENV.TARGETS || './targets.json';
const PUUIDS = new SharedArray('puuids', () => {
  let raw;
  try {
    raw = open(TARGETS_FILE);
  } catch (e) {
    throw new Error(`${TARGETS_FILE} 가 없습니다. 먼저 bash load-test/setup.sh 를 돌리세요.`);
  }
  const puuids = JSON.parse(raw).puuids || [];
  if (puuids.length === 0) {
    throw new Error(`${TARGETS_FILE} 에 소환사가 없습니다. bash load-test/setup.sh 를 다시 돌리세요.`);
  }
  return puuids.slice(0, COUNT);
});

const renewalSeconds = new Trend('renewal_seconds');
const renewalOutcome = new Counter('renewal_outcome');

export const options = {
  scenarios: {
    renewal: {
      executor: 'per-vu-iterations',
      vus: PUUIDS.length,
      iterations: 1,
      maxDuration: '2m',
    },
  },
};

export default function () {
  const puuid = PUUIDS[(__VU - 1) % PUUIDS.length];
  const { status, seconds } = renew(BASE, puuid);
  renewalOutcome.add(1, { outcome: status });
  if (status === 'COOLDOWN') {
    console.log(`${puuid.slice(0, 12)}… 쿨다운이라 갱신하지 않음 (갱신 뒤 2분, 클릭 뒤 10초)`);
    return;
  }
  renewalSeconds.add(seconds);
  check(status, { '갱신 SUCCESS': (s) => s === 'SUCCESS' });
  check(seconds, { [`갱신 ${SUCCESS_SECONDS}초 이내`]: (s) => status === 'SUCCESS' && s <= SUCCESS_SECONDS });

  const matches = http.get(`${BASE}/api/v1/summoners/${puuid}/matches`, { tags: { name: 'matches' } });
  const count = matches.status === 200 ? (matches.json('data.content') || []).length : -1;
  check(count, { '매치 목록 있음': (n) => n > 0 });
  console.log(`${puuid.slice(0, 12)}… 갱신 ${status} (${seconds.toFixed(1)}초), 매치 ${count}건`);
}
