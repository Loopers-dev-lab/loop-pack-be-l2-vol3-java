package com.loopers.application.observability.aspect;

import com.loopers.application.observability.annotation.LogBusinessSuccess;
import com.loopers.application.observability.event.BusinessSuccessObservedEvent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

@Aspect
@Component
public class BusinessSuccessLoggingAspect {

    private final ApplicationEventPublisher applicationEventPublisher;

    public BusinessSuccessLoggingAspect(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Around("@annotation(com.loopers.application.observability.annotation.LogBusinessSuccess)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long elapsed = Math.max(System.currentTimeMillis() - startedAt, 0L);

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        LogBusinessSuccess logBusinessSuccess = method.getAnnotation(LogBusinessSuccess.class);

        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        String memberId = resolveMemberId(logBusinessSuccess.memberIdArg(), parameterNames, args);
        String aggregateId = resolveAggregateId(logBusinessSuccess.aggregateIdArg(), parameterNames, args);
        String traceId = MDC.get("traceId");

        applicationEventPublisher.publishEvent(new BusinessSuccessObservedEvent(
                logBusinessSuccess.action(),
                logBusinessSuccess.domain(),
                signature.getDeclaringTypeName(),
                method.getName(),
                traceId,
                memberId,
                aggregateId,
                elapsed,
                Instant.now()
        ));

        return result;
    }

    private String resolveMemberId(String explicitArg, String[] parameterNames, Object[] args) {
        String explicit = resolveByParameterName(explicitArg, parameterNames, args);
        if (explicit != null) {
            return explicit;
        }
        return resolveByCandidates(parameterNames, args, "memberId", "loginId", "member");
    }

    private String resolveAggregateId(String explicitArg, String[] parameterNames, Object[] args) {
        String explicit = resolveByParameterName(explicitArg, parameterNames, args);
        if (explicit != null) {
            return explicit;
        }
        return resolveByCandidates(parameterNames, args,
                "orderId", "productId", "couponId", "brandId", "categoryId", "paymentId", "id");
    }

    private String resolveByParameterName(String targetName, String[] parameterNames, Object[] args) {
        if (targetName == null || targetName.isBlank() || parameterNames == null || args == null) {
            return null;
        }

        for (int i = 0; i < parameterNames.length; i++) {
            if (!targetName.equals(parameterNames[i])) {
                continue;
            }
            return stringify(args[i]);
        }
        return null;
    }

    private String resolveByCandidates(String[] parameterNames, Object[] args, String... candidates) {
        if (args == null || args.length == 0) {
            return null;
        }

        if (parameterNames != null && parameterNames.length == args.length) {
            for (int i = 0; i < parameterNames.length; i++) {
                String parameterName = parameterNames[i];
                for (String candidate : candidates) {
                    if (candidate.equals(parameterName)) {
                        String value = stringify(args[i]);
                        if (value != null) {
                            return value;
                        }
                    }
                }
            }
        }

        for (Object arg : args) {
            for (String candidate : candidates) {
                String value = tryReadAccessor(arg, candidate);
                if (value != null) {
                    return value;
                }
            }
        }

        return null;
    }

    private String tryReadAccessor(Object target, String accessorName) {
        if (target == null) {
            return null;
        }
        try {
            Method accessor = target.getClass().getMethod(accessorName);
            Object value = accessor.invoke(target);
            return stringify(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof UUID || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }

        try {
            Method valueAccessor = value.getClass().getMethod("value");
            Object nested = valueAccessor.invoke(value);
            if (nested != null) {
                return String.valueOf(nested);
            }
        } catch (Exception ignored) {
        }

        try {
            Method idAccessor = value.getClass().getMethod("id");
            Object nested = idAccessor.invoke(value);
            if (nested != null) {
                return stringify(nested);
            }
        } catch (Exception ignored) {
        }

        return null;
    }
}
