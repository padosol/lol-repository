package com.mmrtr.lol.support.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
public class MdcLoggingAspect {

    private static final String TRACE_ID = "traceId";

    @Around("@within(traceLogging) || @annotation(traceLogging)")
    public Object setTraceId(ProceedingJoinPoint joinPoint, TraceLogging traceLogging) throws Throwable {
        boolean isNew = MDC.get(TRACE_ID) == null;
        if (isNew) {
            MDC.put(TRACE_ID, UUID.randomUUID().toString().substring(0, 8));
        }
        try {
            return joinPoint.proceed();
        } finally {
            if (isNew) {
                MDC.clear();
            }
        }
    }
}
