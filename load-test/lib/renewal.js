// 갱신 한 사이클: 갱신 요청(POST) → 끝날 때까지 상태 조회(GET). renewal-steady.js·renewal.js 가 같이 쓴다.
import http from 'k6/http';
import { sleep } from 'k6';

/** 완료 응답이 SUCCESS 이고 POST 부터 이 시간 안이면 성공이다. */
export const SUCCESS_SECONDS = 3;
/** 이 시간 동안 상태를 본다. 그래도 PROGRESS 면 끝나지 않은 것(timeout)이다. */
export const MAX_WAIT_SECONDS = 5;
const POLL_INTERVAL_SECONDS = 0.5;
const MAX_POLLS = MAX_WAIT_SECONDS / POLL_INTERVAL_SECONDS;

function statusOf(res) {
  if (res.status !== 200) return undefined;
  try {
    return res.json('data.status');
  } catch (e) {
    return undefined;
  }
}

/**
 * 갱신을 요청하고 0.5초마다 상태를 본다 (최대 5초).
 * status: SUCCESS · FAILED · PROGRESS(5초 안에 안 끝남) · COOLDOWN(쿨다운으로 거절) · ERROR(POST 가 202 가 아니거나 응답이 이상함)
 * seconds: POST 부터 마지막 상태 조회까지 걸린 시간
 */
export function renew(base, puuid) {
  const url = `${base}/api/v1/summoners/${puuid}/renewal`;
  const started = Date.now();
  const post = http.post(url, null, { tags: { name: 'renewal-post' } });
  if (statusOf(post) === 'FAILED') return { status: 'COOLDOWN', seconds: 0 };
  if (post.status !== 202) return { status: 'ERROR', seconds: 0 };

  let status = 'PROGRESS';
  for (let i = 0; i < MAX_POLLS && status === 'PROGRESS'; i++) {
    sleep(POLL_INTERVAL_SECONDS);
    status = statusOf(http.get(url, { tags: { name: 'renewal-poll' } })) || 'ERROR';
  }
  return { status, seconds: (Date.now() - started) / 1000 };
}
