// 일정한 갱신 부하: 초당 RATE(기본 1)명을 DURATION 동안 갱신한다. 대상은 setup.sh 가 준비한 targets.json 의 소환사다.
//
// 한 사이클 = 갱신 요청(POST) → 0.5초마다 상태 조회(GET), 최대 5초. 완료 응답이 SUCCESS 이고 POST 부터 3초 이내면 성공이다 (lib/renewal.js).
//
//   bash load-test/setup.sh                                 # 먼저 한 번: 대상 소환사를 DB 에 등록하고 targets.json 을 쓴다
//   bash load-test/run.sh renewal-steady.js                 # 모니터링(Prometheus)으로 지표를 보내며 돈다
//   k6 run -e RATE=2 -e DURATION=3m load-test/renewal-steady.js   # 지표를 보내지 않고 돈다
//
// 같은 소환사는 갱신 뒤 2분 동안 다시 갱신되지 않는다(200 + FAILED → rejected 로 센다).
// 소환사 수 / RATE 가 120초보다 짧으면 한 바퀴 돌아 같은 사람을 다시 부를 때 거절된다.
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';
import { MAX_WAIT_SECONDS, SUCCESS_SECONDS, renew } from './lib/renewal.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8111';
const RATE = Number(__ENV.RATE || 1);
const DURATION = __ENV.DURATION || '5m';

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
      // 한 사이클은 최대 약 5초라 동시에 RATE × 5 개 VU 가 돈다. 여유를 두 배로 잡는다
      preAllocatedVUs: Math.max(10, Math.ceil(RATE * MAX_WAIT_SECONDS * 2)),
      maxVUs: Math.max(50, Math.ceil(RATE * MAX_WAIT_SECONDS * 6)),
    },
  },
  thresholds: {
    [`renewal_cycle_success`]: [`rate>=${__ENV.SUCCESS_RATE || 0.99}`],
  },
};

// renew() 의 status → 사이클 결과. 쿨다운 거절은 사이클이 시작되지 않은 것이라 성공률에 넣지 않는다.
const OUTCOME = { FAILED: 'failed', PROGRESS: 'timeout', COOLDOWN: 'rejected', ERROR: 'error' };

export default function () {
  const puuid = PUUIDS[exec.scenario.iterationInTest % PUUIDS.length];
  const { status, seconds } = renew(BASE, puuid);

  if (status === 'SUCCESS') {
    cycleSeconds.add(seconds);
    const inTime = seconds <= SUCCESS_SECONDS;
    cycleOutcome.add(1, { outcome: inTime ? 'success' : 'slow' });
    cycleSuccess.add(inTime);
    return;
  }
  cycleOutcome.add(1, { outcome: OUTCOME[status] || 'error' });
  if (status !== 'COOLDOWN') {
    cycleSuccess.add(false);
  }
}
