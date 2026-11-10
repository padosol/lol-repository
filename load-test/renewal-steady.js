// 일정한 갱신 부하: 초당 RATE(기본 1)명을 DURATION 동안 갱신한다. 대상은 setup.sh 가 준비한 targets.json 의 소환사다.
//
// 한 사이클 = 갱신 요청(POST) → 완료될 때까지 상태 조회(GET). 완료 응답이 SUCCESS 이고 POST 부터 3초 이내면 성공이다.
//
//   bash load-test/setup.sh                                 # 먼저 한 번: 대상 소환사를 DB 에 등록하고 targets.json 을 쓴다
//   bash load-test/run.sh renewal-steady.js                 # 모니터링(Prometheus)으로 지표를 보내며 돈다
//   k6 run -e RATE=2 -e DURATION=3m load-test/renewal-steady.js   # 지표를 보내지 않고 돈다
//
// 같은 소환사는 갱신 뒤 2분 동안 다시 갱신되지 않는다(200 + FAILED → rejected 로 센다).
// 소환사 수 / RATE 가 120초보다 짧으면 한 바퀴 돌아 같은 사람을 다시 부를 때 거절된다.
import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8111';
const RATE = Number(__ENV.RATE || 1);
const DURATION = __ENV.DURATION || '5m';
const SUCCESS_SECONDS = Number(__ENV.SUCCESS_SECONDS || 3);
// 상태 조회 간격(초). 사이클 시간은 이 간격 단위로 잡힌다.
const POLL_INTERVAL = Number(__ENV.POLL_INTERVAL || 0.5);
// 이 시간이 지나도 PROGRESS 면 timeout 으로 센다.
const POLL_CAP_SECONDS = Number(__ENV.POLL_CAP_SECONDS || 10);
const COOLDOWN_SECONDS = 120;

if (!(RATE > 0)) {
  throw new Error(`RATE 는 양수여야 합니다: ${__ENV.RATE}`);
}

// setup.sh 가 쓴 대상 목록 ({ puuids: [...] }). DB 에 있는 것까지 확인된 소환사만 들어 있다. 다른 파일은 -e TARGETS=/절대/경로 로 준다.
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
  return puuids;
});

const cycleSeconds = new Trend('renewal_cycle_seconds');
const cycleSuccess = new Rate('renewal_cycle_success');
const cycleOutcome = new Counter('renewal_cycle_outcome');

export const options = {
  // url 태그는 소환사마다 달라 Prometheus 시리즈가 수백 개로 는다. 요청은 name 태그로 묶는다.
  systemTags: ['status', 'method', 'name', 'scenario', 'expected_response', 'check', 'error_code'],
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(10, Math.ceil(RATE * POLL_CAP_SECONDS)),
      maxVUs: Math.max(50, Math.ceil(RATE * POLL_CAP_SECONDS * 3)),
    },
  },
  thresholds: {
    [`renewal_cycle_success`]: [`rate>=${__ENV.SUCCESS_RATE || 0.99}`],
  },
};

/** 대상 수만 알린다. 소환사 등록은 setup.sh 가 미리 한다 (부하 직전에 Riot 을 부르지 않는다). */
export function setup() {
  console.log(`대상 ${PUUIDS.length}명 (${TARGETS_FILE})`);
  const revisitSeconds = PUUIDS.length / RATE;
  if (revisitSeconds < COOLDOWN_SECONDS) {
    console.warn(`같은 소환사를 ${revisitSeconds.toFixed(0)}초마다 다시 부릅니다. ${COOLDOWN_SECONDS}초 쿨다운에 걸려 rejected 가 나옵니다.`);
  }
}

function statusOf(res) {
  try {
    return res.json('data.status');
  } catch (e) {
    return undefined;
  }
}

function finish(outcome, success) {
  cycleOutcome.add(1, { outcome });
  if (success !== undefined) {
    cycleSuccess.add(success);
  }
}

export default function () {
  const puuid = PUUIDS[exec.scenario.iterationInTest % PUUIDS.length];
  const url = `${BASE}/api/v1/summoners/${puuid}/renewal`;

  const started = Date.now();
  const post = http.post(url, null, { tags: { name: 'renewal-post' } });
  if (post.status === 200 && statusOf(post) === 'FAILED') {
    // 쿨다운 거절은 사이클이 시작되지 않은 것이라 성공률에 넣지 않는다
    finish('rejected');
    return;
  }
  if (post.status !== 202) {
    finish('error', false);
    return;
  }

  let status = 'PROGRESS';
  while (status === 'PROGRESS' && (Date.now() - started) / 1000 < POLL_CAP_SECONDS) {
    sleep(POLL_INTERVAL);
    const poll = http.get(url, { tags: { name: 'renewal-poll' } });
    status = poll.status === 200 ? statusOf(poll) : undefined;
  }
  const seconds = (Date.now() - started) / 1000;

  if (status === 'SUCCESS') {
    cycleSeconds.add(seconds);
    const inTime = seconds <= SUCCESS_SECONDS;
    finish(inTime ? 'success' : 'slow', inTime);
  } else if (status === 'FAILED') {
    finish('failed', false);
  } else if (status === 'PROGRESS') {
    finish('timeout', false);
  } else {
    finish('error', false);
  }
}
