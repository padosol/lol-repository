package com.mmrtr.lol.support.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ExecutionTimeAspect {

    @Around("@within(logExecutionTime) || @annotation(logExecutionTime)")
    public Object measureExecutionTime(ProceedingJoinPoint joinPoint,
                                       LogExecutionTime logExecutionTime) throws Throwable {
        Logger log = LoggerFactory.getLogger(joinPoint.getTarget().getClass());

        String methodName = joinPoint.getSignature().toShortString();
        long start = System.currentTimeMillis();

        try {
            return joinPoint.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            log.info("[{}] 실행시간: {}ms", methodName, elapsed);
        }
    }
}
