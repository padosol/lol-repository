// 실행 판정. lab.sh run·verify 가 START·END(unix 초)와 SCENARIO 를 넘긴다.
// 한 줄에 `<등급>  <항목>  <값>` 을 찍는다.
//   판정 항목: PASS 또는 FAIL. FAIL 이 하나라도 있으면 종료 코드 99 다.
//   처리 항목: WARN(나쁨) 또는 INFO. 1단계에서는 실행을 실패시키지 않는다.
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { POLL_CAP_SECONDS, PROM } from './lib/lab.js';

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

// consumer 가 있는 vhost lab 큐만 고른다. consumer 가 없는 큐(이 브랜치의 renewal.match.find.queue 등)는 늘 쌓인다.
const CONSUMED = 'and on (vhost, queue) (rabbitmq_queue_consumers{vhost="lab"} > 0)';

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

// 결과가 비면 null 이다.
function scalarOrNull(q) {
  const result = query(q);
  return result.length === 0 ? null : Number(result[0].value[1]);
}

function line(level, item, value) {
  console.log(`${level}  ${item}  ${value}`);
}

function judge(ok, item, value) {
  check(null, { [item]: () => ok });
  line(ok ? 'PASS' : 'FAIL', item, value);
}

function report(bad, item, value) {
  line(bad ? 'WARN' : 'INFO', item, value);
}

function count(v) {
  return String(Math.round(v));
}

export default function () {
  // 판정 — 가짜 서버 지표가 비면 통과시키지 않는다
  const up = scalarOrNull(`min_over_time(up{job="riot-mock"}[${R}])`);
  judge(up === 1, '가짜 서버 수집 (창 전체 up)', up === null ? '없음' : count(up));

  // 429 카운터는 첫 429 때 생기므로 결과가 비면 0 이다
  const rateLimited = scalarOrNull(delta('riot_mock_rate_limited_total{type=~"application|method"}')) || 0;
  judge(rateLimited === 0, '가짜 서버 429 (application·method)', count(rateLimited));

  // run 이 시작할 때 가짜 서버를 reset 하므로 END 시각 값이 그 실행의 최댓값이다
  const peak = scalarOrNull('max(riot_mock_sliding_peak_ratio)');
  judge(peak !== null && peak < 1, '10초 구간 최대 사용률 (한도 대비)', peak === null ? '없음' : peak.toFixed(3));

  const calls = scalarOrNull(delta('riot_mock_requests_total')) || 0;
  judge(calls > 0, '총 Riot 호출', count(calls));

  // 처리 — WARN 이어도 1단계에서는 실행을 실패시키지 않는다
  // 지난 실행에서 생긴 시리즈는 이번 창에 0 으로 남으므로 뺀다
  const renewals = query(delta('lol_renewal_completed_total', ' by (result, reason)'))
    .filter((r) => Number(r.value[1]) !== 0);
  if (renewals.length === 0) {
    report(true, '앱 갱신 결과', '없음');
  }
  for (const r of renewals) {
    const { result, reason } = r.metric;
    report(result === 'failed', `앱 갱신 결과 ${result}/${reason}`, count(Number(r.value[1])));
  }

  const outcomes = query(k6Last('k6_lab_renewal_outcome_total', ' by (outcome)'));
  if (outcomes.length === 0) {
    report(true, 'k6 갱신 결과', '없음');
  } else {
    const by = {};
    for (const r of outcomes) {
      by[r.metric.outcome] = Math.round(Number(r.value[1]));
    }
    const n = (o) => by[o] || 0;
    const text = `끝남(성공·실패 포함)=${n('done')} timeout=${n('timeout')} rejected=${n('rejected')} error=${n('error')}`;
    report(n('timeout') > 0 || n('error') > 0, 'k6 갱신 결과', text);
  }

  const p95 = scalarOrNull(k6Last('k6_lab_renewal_done_seconds_p95'));
  if (p95 === null) {
    report(true, '갱신 끝남 p95', '없음');
  } else {
    const capped = p95 >= POLL_CAP_SECONDS;
    report(capped, '갱신 끝남 p95', `${p95.toFixed(2)}s${capped ? ` (폴링 상한 ${POLL_CAP_SECONDS}s 에 걸림)` : ''}`);
  }

  const waiting = scalarOrNull(`max_over_time(sum(rabbitmq_queue_messages_ready{vhost="lab"} ${CONSUMED})[${R}:5s])`);
  report(waiting === null || waiting > 0, '갱신 큐 최대 대기 (consumer 있는 큐)', waiting === null ? '없음' : count(waiting));

  const left = scalarOrNull(`sum(rabbitmq_queue_messages{vhost="lab"} ${CONSUMED})`);
  report(left === null || left > 0, 'END 에 남은 갱신 (대기+처리 중)', left === null ? '없음' : count(left));
}

// 기본 요약 대신 위의 표만 남긴다
export function handleSummary() {
  return {};
}
