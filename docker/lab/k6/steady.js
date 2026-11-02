// 일정한 갱신 부하. 초당 RATE(기본 10)건을 DURATION(기본 5m) 동안, 첫 방문 30% · 재방문 70% 로 보낸다.
import exec from 'k6/execution';
import { SYSTEM_TAGS, pickN, puuidOf, renewAndPoll } from './lib/lab.js';

const RATE = Number(__ENV.RATE || 10);
const DURATION = __ENV.DURATION || '5m';
const FIRST_RATIO = 0.3;

if (!Number.isInteger(RATE) || RATE <= 0) {
  throw new Error(`RATE 는 양의 정수여야 합니다: ${__ENV.RATE}`);
}

export const options = {
  systemTags: SYSTEM_TAGS,
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 100,
      maxVUs: 400,
    },
  },
};

export default function () {
  renewAndPoll(puuidOf(pickN(exec.scenario.iterationInTest, FIRST_RATIO)));
}
