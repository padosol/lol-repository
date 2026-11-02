// 시작 상태 만들기. lab.sh seed 가 PHASE=create, PHASE=warm 순서로 부른다.
//   create: lab1..lab4000 을 Riot ID 로 조회해 소환사를 저장한다.
//   warm:   재방문 풀(2501..4000)의 첫 갱신을 초당 5건으로 요청만 한다 (큐가 비는 것은 lab.sh 가 기다린다).
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { APP, RETURNING, SYSTEM_TAGS, puuidOf } from './lib/lab.js';

const PLAYERS = 4000;
// 초당 50건이면 consumer 20개가 한도 없이 돌아 매치 저장이 동시에 10건 넘게 겹친다. 이때 MatchService.saveAll 이
// 커넥션을 쥔 채 다른 스레드의 저장(각자 커넥션 필요)을 기다려 Hikari 풀(15)이 30초 동안 막힌다. 5건이면 2~3건만 겹친다.
const WARM_RATE = 5;
const WARM_COUNT = RETURNING.to - RETURNING.from + 1;

const PHASES = {
  create: {
    scenario: { executor: 'shared-iterations', iterations: PLAYERS, vus: 50, maxDuration: '30m' },
    requests: PLAYERS,
    run: create,
  },
  warm: {
    scenario: {
      executor: 'constant-arrival-rate',
      rate: WARM_RATE,
      timeUnit: '1s',
      duration: `${WARM_COUNT / WARM_RATE}s`,
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
    requests: WARM_COUNT,
    run: warm,
  },
};

const phase = PHASES[__ENV.PHASE];
if (!phase) {
  throw new Error(`PHASE 는 create 또는 warm 이어야 합니다: ${__ENV.PHASE}`);
}

// 소환사마다 정확히 한 번 보냈는지 센다. constant-arrival-rate 는 rate·duration 보다 반복을 하나 더 만들 수 있다.
const seedRequests = new Counter('lab_seed_requests');

export const options = {
  systemTags: SYSTEM_TAGS,
  scenarios: { [__ENV.PHASE]: phase.scenario },
  // 하나라도 실패하거나 빠지면 종료 코드 99 로 끝난다
  thresholds: {
    checks: ['rate==1'],
    lab_seed_requests: [`count==${phase.requests}`],
  },
};

export default function () {
  phase.run(exec.scenario.iterationInTest);
}

function create(iter) {
  const n = iter + 1;
  const res = http.get(`${APP}/api/riot/kr/summoners/lab${n}/LAB`, { tags: { name: 'seed-create' } });
  seedRequests.add(1);
  const ok = check(res, {
    '소환사 조회 200': (r) => r.status === 200,
    'puuid 일치': (r) => r.status === 200 && r.json('puuid') === puuidOf(n),
  });
  if (!ok) {
    exec.test.abort(`lab${n}#LAB 조회 실패: status=${res.status}`);
  }
}

function warm(iter) {
  const n = RETURNING.from + iter;
  if (n > RETURNING.to) {
    return;
  }
  const res = http.post(`${APP}/api/v1/summoners/${puuidOf(n)}/renewal`, null, { tags: { name: 'seed-warm' } });
  seedRequests.add(1);
  check(res, { '갱신 접수 202': (r) => r.status === 202 });
}
