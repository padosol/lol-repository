// 실험실 k6 스크립트가 함께 쓰는 주소·소환사 선택·갱신 흐름.
import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// 앱 주소는 lab.sh 가 자기 APP_URL 을 컨테이너에서 닿는 주소로 바꿔 넘긴다. 기본값을 두지 않아 둘이 어긋나지 않는다.
export const APP = __ENV.APP_URL;
if (!APP) {
  throw new Error('APP_URL 이 없습니다. bash docker/lab/lab.sh 로 돌리세요 (직접 돌리면 -e APP_URL=http://host.docker.internal:8111)');
}
export const MOCK = 'http://riot-mock:8099';
export const PROM = 'http://prometheus:9090';

export const FIRST = { from: 1, to: 2500 };
export const RETURNING = { from: 2501, to: 4000 };

// url 태그는 소환사마다 달라 Prometheus 시리즈가 수천 개로 는다. 요청은 name 태그로 묶고 url 은 보내지 않는다.
export const SYSTEM_TAGS = ['status', 'method', 'name', 'scenario', 'expected_response', 'check', 'error_code'];

// 누적 10초
const POLL_INTERVALS = [1, 1.5, 2, 2, 2, 1.5];
/** 폴링을 그만두는 시각(초). 갱신 완료 시간은 이 값에서 잘린다. */
export const POLL_CAP_SECONDS = POLL_INTERVALS.reduce((a, b) => a + b, 0);

const renewalDone = new Trend('lab_renewal_done_seconds');
const renewalOutcome = new Counter('lab_renewal_outcome');

export function puuidOf(n) {
  return 'lab' + String(n).padStart(75, '0');
}

/** 반복 번호 iter 를 소환사 번호로. 10번 중 round(firstRatio·10)번은 첫 방문, 나머지는 재방문이다. */
export function pickN(iter, firstRatio) {
  const k = Math.round(firstRatio * 10);
  const slot = iter % 10;
  const block = Math.floor(iter / 10);
  if (slot < k) {
    const n = FIRST.from + block * k + slot;
    if (n > FIRST.to) {
      exec.test.abort('첫 방문 풀 소진');
    }
    return n;
  }
  const size = RETURNING.to - RETURNING.from + 1;
  return RETURNING.from + ((block * (10 - k) + slot - k) % size);
}

/** 응답의 data.status. JSON 이 아니면 undefined 다. */
export function renewalStatusOf(res) {
  try {
    return res.json('data.status');
  } catch (e) {
    return undefined;
  }
}

/**
 * 갱신을 요청하고 끝날 때까지(최대 10초) 폴링한다. 결과는 lab_renewal_outcome 의 outcome 태그로 센다.
 * done 은 PROGRESS 가 끝났다는 뜻일 뿐이다 (앱 쪽 실패도 done). 성공·실패는 앱의 lol_renewal_completed_total 로 본다.
 */
export function renewAndPoll(puuid) {
  const url = `${APP}/api/v1/summoners/${puuid}/renewal`;
  const started = Date.now();
  const res = http.post(url, null, { tags: { name: 'renewal-post' } });
  if (res.status === 200 && renewalStatusOf(res) === 'FAILED') {
    renewalOutcome.add(1, { outcome: 'rejected' });
    return;
  }
  if (res.status !== 202) {
    renewalOutcome.add(1, { outcome: 'error' });
    return;
  }
  for (const wait of POLL_INTERVALS) {
    sleep(wait);
    const poll = http.get(url, { tags: { name: 'renewal-poll' } });
    const status = poll.status === 200 ? renewalStatusOf(poll) : undefined;
    if (status === undefined) {
      renewalOutcome.add(1, { outcome: 'error' });
      return;
    }
    if (status !== 'PROGRESS') {
      renewalDone.add((Date.now() - started) / 1000);
      renewalOutcome.add(1, { outcome: 'done' });
      return;
    }
  }
  renewalOutcome.add(1, { outcome: 'timeout' });
}
