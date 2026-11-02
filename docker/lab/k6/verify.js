// 실행 판정. lab.sh run·verify 가 START·END(unix 초)와 SCENARIO, LAB_QUEUES 를 넘긴다.
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

// 갱신 처리를 보는 vhost lab 큐. lab.sh 의 LAB_QUEUES(공백 구분) 그대로다. consumer 수가 아니라 이름으로 고른다.
const QUEUES = (__ENV.LAB_QUEUES || '').split(/[\s,]+/).filter((q) => q.length > 0);
if (QUEUES.length === 0 || QUEUES.some((q) => !/^[A-Za-z0-9._-]+$/.test(q))) {
  throw new Error(`LAB_QUEUES 가 필요합니다 (예: mmrtr.summoner): ${__ENV.LAB_QUEUES}`);
}
// PromQL 문자열 안의 정규식이라 점을 \\. 로 쓴다
const QUEUE_SELECTOR = `vhost="lab",queue=~"${QUEUES.map((q) => q.replace(/\./g, '\\\\.')).join('|')}"`;
const QUEUE_LABEL = QUEUES.join(',');

// 가짜 서버 application 한도(10초 창)의 상한. 기본값 500:10 보다 느슨하면 판정이 의미가 없다 (더 엄격한 한도는 된다).
const APP_LIMIT_CEILING = 500;
const APP_LIMIT_10S = 'riot_mock_window_limit{bucket=~".+[|]app",window="10"}';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ['rate==1'] },
};

// 가짜 서버·앱 카운터의 [START, END] 증가량 = END 값 − START 값.
// increase() 는 창 안에서 처음 생긴 시리즈의 첫 값을 세지 않는다 (가짜 서버를 다시 띄운 뒤 처음 쓰는 routing 등).
// 그래서 START 에 없던 시리즈는 END 값을 그대로 더한다. 창 안에서 그 프로세스가 재시작하면 맞지 않는다.
function delta(selector, by = '') {
  return `sum${by} ((${selector} - ${selector} offset ${R}) or (${selector} unless ${selector} offset ${R}))`;
}

// k6 지표는 실행마다 0 에서 시작하므로 이 실행의 마지막 값을 쓴다.
function k6Last(name, by = '') {
  return `sum${by} (last_over_time(${name}{scenario="${SCENARIO}"}[${R}]))`;
}

// 게이지의 창 전체 최댓값·최솟값. 1초 간격으로 본다 (Prometheus 수집 주기와 같다).
function maxOver(expr) {
  return `max_over_time((${expr})[${R}:1s])`;
}

function minOver(expr) {
  return `min_over_time((${expr})[${R}:1s])`;
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

  // 창 안에서 적용된 application 10초 한도. 하나라도 500 보다 느슨했거나 시리즈가 없으면 FAIL 이다
  const limitMax = scalarOrNull(maxOver(`max(${APP_LIMIT_10S})`));
  const limitMin = scalarOrNull(minOver(`min(${APP_LIMIT_10S})`));
  judge(limitMax !== null && limitMax <= APP_LIMIT_CEILING,
    `가짜 서버 application 한도 (10초 창, ${APP_LIMIT_CEILING} 이하)`,
    limitMax === null ? '없음' : `min=${count(limitMin)} max=${count(limitMax)}`);

  // 429 카운터는 routing 의 첫 요청 때 0 으로 생긴다. 결과가 비면(창 안에 요청이 없었다) 0 이다
  const rateLimited = scalarOrNull(delta('riot_mock_rate_limited_total{type=~"application|method"}')) || 0;
  judge(rateLimited === 0, '가짜 서버 429 (application·method)', count(rateLimited));

  // 창 전체의 최댓값이다. 창 안에서 application 한도를 바꾸면 추적기가 비워져도 그 전 값이 남는다
  const peak = scalarOrNull(maxOver('max(riot_mock_sliding_peak_ratio)'));
  judge(peak !== null && peak < 1, '10초 구간 최대 사용률 (한도 대비)', peak === null ? '없음' : peak.toFixed(3));

  const calls = scalarOrNull(delta('riot_mock_requests_total')) || 0;
  judge(calls > 0, '총 Riot 호출', count(calls));

  // 처리 — WARN 이어도 1단계에서는 실행을 실패시키지 않는다
  const appUp = scalarOrNull(`min_over_time(up{job="lol-app"}[${R}])`);
  report(appUp === null || appUp < 1, '앱 수집 (창 전체 up)', appUp === null ? '없음' : count(appUp));

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

  const waiting = scalarOrNull(maxOver(`sum(rabbitmq_queue_messages_ready{${QUEUE_SELECTOR}})`));
  report(waiting === null || waiting > 0, `갱신 큐 최대 대기 (${QUEUE_LABEL})`, waiting === null ? '없음' : count(waiting));

  const left = scalarOrNull(`sum(rabbitmq_queue_messages{${QUEUE_SELECTOR}})`);
  report(left === null || left > 0, `END 에 남은 갱신 (${QUEUE_LABEL}, 대기+처리 중)`, left === null ? '없음' : count(left));
}

// 기본 요약 대신 위의 표만 남긴다
export function handleSummary() {
  return {};
}
