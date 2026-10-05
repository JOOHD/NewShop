package JOO.jooshop.global.config.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class PerformanceAspect {

    // 대상 메서드의 실행 시간을 측정해 로그로 남김
    @Around("execution(* JOO.jooshop.product.controller..*(..)) || execution(* JOO.jooshop.productVariant.controller..*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        Object proceed = joinPoint.proceed();

        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        log.info("{} executed in {} ms", joinPoint.getSignature(), executionTime);

        return proceed;
    }
}
