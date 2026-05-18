package com.ecommerce.engine.aspect;

import com.ecommerce.engine.config.MonitoringProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PerformanceMonitoringAspect {

    private final MeterRegistry meterRegistry;
    private final MonitoringProperties monitoringProperties;

    @Around("execution(* com.ecommerce.engine.controller..*(..)) || execution(* com.ecommerce.engine.service..*(..)) || execution(* com.ecommerce.engine.repository..*(..))")
    public Object monitorExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        String outcome = "success";
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            outcome = throwable.getClass().getSimpleName();
            throw throwable;
        } finally {
            long elapsedNanos = System.nanoTime() - start;
            recordMetrics(joinPoint, elapsedNanos, outcome);
            logIfSlow(joinPoint, elapsedNanos, outcome);
        }
    }

    private void recordMetrics(ProceedingJoinPoint joinPoint, long elapsedNanos, String outcome) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Timer.builder("commerce.method.execution")
            .description("Execution time of instrumented commerce methods")
            .tag("class", signature.getDeclaringType().getSimpleName())
            .tag("method", signature.getMethod().getName())
            .tag("outcome", outcome)
            .register(meterRegistry)
            .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    private void logIfSlow(ProceedingJoinPoint joinPoint, long elapsedNanos, String outcome) {
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        if (elapsedMillis >= monitoringProperties.getSlowCallThresholdMs()) {
            log.warn("Slow method detected: {}.{} took {} ms [{}]",
                signature.getDeclaringType().getSimpleName(),
                signature.getMethod().getName(),
                elapsedMillis,
                outcome);
        } else {
            log.debug("Method {}.{} took {} ms [{}]",
                signature.getDeclaringType().getSimpleName(),
                signature.getMethod().getName(),
                elapsedMillis,
                outcome);
        }
    }
}
