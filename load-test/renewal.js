// 실제 Riot API 갱신 확인용 k6 시작 스크립트.
// 흐름: (setup) Riot ID → puuid 조회 → (반복마다) 갱신 요청 → 상태 폴링 → 매치 목록 확인.
//
//   k6 run -e RIOT_IDS='Hide on bush-KR1,다른이름-태그' load-test/renewal.js
//
// 앱은 local 프로파일(공개 포트 8111)로 실제 RIOT_API_KEY 를 넣어 띄워 둔다. 다른 주소면 -e BASE_URL=... 로 바꾼다.
//
// 같은 소환사는 갱신 뒤 2분, 클릭 뒤 10초 동안 다시 갱신되지 않는다(200 + FAILED 로 돌아온다).
// 그래서 기본은 Riot ID 마다 한 번씩만 돈다.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8111';
const PLATFORM = __ENV.PLATFORM || 'kr';
const RIOT_IDS = (__ENV.RIOT_IDS || 'Hide on bush-KR1').split(',').map((s) => s.trim()).filter(Boolean);
const POLL_TIMEOUT_S = Number(__ENV.POLL_TIMEOUT_S || 30);

const renewalSeconds = new Trend('renewal_seconds');
const renewalOutcome = new Counter('renewal_outcome');

export const options = {
  scenarios: {
    renewal: {
      executor: 'per-vu-iterations',
      vus: RIOT_IDS.length,
      iterations: 1,
      maxDuration: '2m',
    },
  },
};

export function setup() {
  return RIOT_IDS.map((riotId) => {
    const res = http.get(
      `${BASE}/api/v1/summoners/by-riot-id/${encodeURIComponent(riotId)}?platform=${PLATFORM}`,
      { tags: { name: 'by-riot-id' } },
    );
    check(res, { 'by-riot-id 200': (r) => r.status === 200 });
    const puuid = res.status === 200 ? res.json('data.puuid') : null;
    console.log(`${riotId} → ${puuid ?? `조회 실패 ${res.status} ${res.body}`}`);
    return puuid;
  });
}

export default function (puuids) {
  const puuid = puuids[(__VU - 1) % puuids.length];
  if (!puuid) return;

  const url = `${BASE}/api/v1/summoners/${puuid}/renewal`;
  const started = Date.now();
  const post = http.post(url, null, { tags: { name: 'renewal-post' } });
  if (post.status === 200 && post.json('data.status') === 'FAILED') {
    renewalOutcome.add(1, { outcome: 'cooldown' });
    console.log(`${puuid.slice(0, 12)}… 쿨다운이라 갱신하지 않음 (갱신 뒤 2분, 클릭 뒤 10초)`);
    return;
  }
  check(post, { 'renewal 202': (r) => r.status === 202 });

  let status = 'PROGRESS';
  while (status === 'PROGRESS' && Date.now() - started < POLL_TIMEOUT_S * 1000) {
    sleep(0.5);
    const poll = http.get(url, { tags: { name: 'renewal-poll' } });
    status = poll.status === 200 ? poll.json('data.status') : `HTTP ${poll.status}`;
  }
  renewalSeconds.add((Date.now() - started) / 1000);
  renewalOutcome.add(1, { outcome: status === 'SUCCESS' ? 'done' : String(status) });
  check(status, { '갱신 SUCCESS': (s) => s === 'SUCCESS' });

  const matches = http.get(`${BASE}/api/v1/summoners/${puuid}/matches`, { tags: { name: 'matches' } });
  const count = matches.status === 200 ? (matches.json('data.content') || []).length : -1;
  check(count, { '매치 목록 있음': (n) => n > 0 });
  console.log(`${puuid.slice(0, 12)}… 갱신 ${status}, 매치 ${count}건`);
}
