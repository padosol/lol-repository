// 실행 판정. lab.sh run·verify 가 START·END(unix 초)와 SCENARIO 를 넘긴다.
// 한 줄에 `FAIL|WARN|PASS  <항목>  <값>` 을 찍는다. FAIL 이 하나라도 있으면 종료 코드 99 다.
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { PROM } from './lib/lab.js';

const START = Number(__ENV.START);
const END = Number(__ENV.END);
const SCENARIO = __ENV.SCENARIO || '';
if (!Number.isInteger(START) || !Number.isInteger(END) || END <= START) {
  throw new Error(`START·END 는 START < END 인 unix 초여야 합니다: START=${__ENV.START} END=${__ENV.END}`);
}
if (!/^[a-z][a-z0-9-]*$/.test(SCENARIO)) {
  throw new Error(`SCENARIO 가 필요합니다 (예: steady): ${__ENV.SCENARIO}`);
}
const R = `${END - START}s`;

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ['rate==1'] },
};

// 가짜 서버·앱 카운터의 [START, END] 증가량 = END 값 − START 값.
// increase() 는 창 안에서 처음 생긴 시리즈의 첫 값을 세지 않는다 (가짜 서버 429 카운터는 첫 429 때 생긴다).
// 그래서 START 에 없던 시리즈는 END 값을 그대로 더한다. 창 안에서 그 프로세스가 재시작하면 맞지 않는다.
function delta(selector, by = '') {
  return `sum${by} ((${selector} - ${selector} offset ${R}) or (${selector} unless ${selector} offset ${R}))`;
}

// k6 지표는 실행마다 0 에서 시작하므로 이 실행의 마지막 값을 쓴다.
function k6Last(name, by = '') {
  return `sum${by} (last_over_time(${name}{scenario="${SCENARIO}"}[${R}]))`;
}

function query(q) {
  const res = http.get(`${PROM}/api/v1/query?query=${encodeURIComponent(q)}&time=${END}`);
  let body = null;
  try {
    body = res.json();
  } catch (e) {
    body = null;
  }
  if (res.status !== 200 || !body || body.status !== 'success') {
    line('FAIL', 'Prometheus 질의', `status=${res.status} ${q}`);
    exec.test.abort('Prometheus 질의 실패');
  }
  return body.data.result;
}

// 결과가 비면 0 이다.
function scalar(q) {
  const result = query(q);
  return result.length === 0 ? 0 : Number(result[0].value[1]);
}

function line(level, item, value) {
  console.log(`${level}  ${item}  ${value}`);
}

function judge(ok, item, value) {
  check(null, { [item]: () => ok });
  line(ok ? 'PASS' : 'FAIL', item, value);
}

function count(v) {
  return String(Math.round(v));
}

export default function () {
  // 판정
  const rateLimited = scalar(delta('riot_mock_rate_limited_total{type=~"application|method"}'));
  judge(rateLimited === 0, '가짜 서버 429 (application·method)', count(rateLimited));

  // run 이 시작할 때 가짜 서버를 reset 하므로 END 시각 값이 그 실행의 최댓값이다
  const peak = scalar('max(riot_mock_sliding_peak_ratio)');
  judge(peak < 1, '10초 구간 최대 사용률 (한도 대비)', peak.toFixed(3));

  // 보고
  // 지난 실행에서 생긴 시리즈는 이번 창에 0 으로 남으므로 뺀다
  const renewals = query(delta('lol_renewal_completed_total', ' by (result, reason)'))
    .filter((r) => Number(r.value[1]) !== 0);
  if (renewals.length === 0) {
    line('WARN', '앱 갱신 결과', '없음');
  }
  for (const r of renewals) {
    const { result, reason } = r.metric;
    line(result === 'failed' ? 'WARN' : 'PASS', `앱 갱신 결과 ${result}/${reason}`, count(Number(r.value[1])));
  }

  const outcomes = query(k6Last('k6_lab_renewal_outcome_total', ' by (outcome)'));
  const byOutcome = {};
  for (const r of outcomes) {
    byOutcome[r.metric.outcome] = count(Number(r.value[1]));
  }
  if (outcomes.length > 0) {
    const text = ['done', 'timeout', 'rejected', 'error'].map((o) => `${o}=${byOutcome[o] || 0}`).join(' ');
    line(byOutcome.error ? 'WARN' : 'PASS', 'k6 갱신 결과', text);
  }

  const p95 = query(k6Last('k6_lab_renewal_done_seconds_p95'));
  if (p95.length === 0) {
    line('WARN', '갱신 완료 p95', '없음');
  } else {
    line('PASS', '갱신 완료 p95', `${Number(p95[0].value[1]).toFixed(2)}s`);
  }

  const backlog = scalar(`max_over_time(sum(rabbitmq_queue_messages{vhost="lab"})[${R}:5s])`);
  line('PASS', 'vhost lab 큐 최대 적체', count(backlog));

  const calls = scalar(delta('riot_mock_requests_total'));
  line(calls > 0 ? 'PASS' : 'WARN', '총 Riot 호출', count(calls));
}

// 기본 요약 대신 위의 표만 남긴다
export function handleSummary() {
  return {};
}
