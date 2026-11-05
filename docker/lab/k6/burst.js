// 갱신이 몰려오는 부하. BURST_INTERVAL(기본 30초)마다 BURST_SIZE(기본 6)건을 같은 시각에 보내고, BURST_ROUNDS(기본 10)번 반복한다. 모두 첫 방문이다.
import exec from 'k6/execution';
import { sleep } from 'k6';
import { SYSTEM_TAGS, FIRST, puuidOf, renewAndPoll } from './lib/lab.js';

const SIZE = Number(__ENV.BURST_SIZE || 6);
const INTERVAL = Number(__ENV.BURST_INTERVAL || 30);
const ROUNDS = Number(__ENV.BURST_ROUNDS || 10);

for (const [name, value] of [['BURST_SIZE', SIZE], ['BURST_INTERVAL', INTERVAL], ['BURST_ROUNDS', ROUNDS]]) {
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name} 는 양의 정수여야 합니다: ${__ENV[name]}`);
  }
}

export const options = {
  systemTags: SYSTEM_TAGS,
  scenarios: {
    burst: {
      executor: 'per-vu-iterations',
      vus: SIZE,
      iterations: ROUNDS,
      maxDuration: `${ROUNDS * INTERVAL + 60}s`,
    },
  },
};

export function setup() {
  return { start: Date.now() + 2000 };
}

export default function (data) {
  const round = exec.vu.iterationInScenario;
  const wait = data.start + round * INTERVAL * 1000 - Date.now();
  if (wait > 0) {
    sleep(wait / 1000);
  }
  const n = FIRST.from + round * SIZE + (exec.vu.idInTest - 1);
  if (n > FIRST.to) {
    exec.test.abort('첫 방문 풀 소진');
  }
  renewAndPoll(puuidOf(n));
}
