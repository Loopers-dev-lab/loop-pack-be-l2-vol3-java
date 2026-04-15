package com.loopers.batch.job.ranking.param;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

/**
 * anchorDate 파라미터로부터 롤링 윈도우 경계를 계산하여
 * JobExecution 의 ExecutionContext 에 주입한다.
 *
 * 이후 Step 의 Reader 쿼리는 {@code @Value("#{jobExecutionContext['last7dStart']}")} 방식으로
 * 이 값을 바인딩받아 사용한다.
 *
 * 재시작 시 ExecutionContext 는 유지되므로, beforeJob 은 최초 실행 시에만 기록하도록
 * 이미 값이 있으면 덮어쓰지 않는다.
 */
@Component
public class RankingJobParametersListener implements JobExecutionListener {

    public static final String PARAM_ANCHOR_DATE = "anchorDate";

    public static final String CTX_ANCHOR_DATE_KEY = "anchorDateKey";
    public static final String CTX_LAST_7D_START   = "last7dStart";
    public static final String CTX_LAST_7D_END     = "last7dEnd";
    public static final String CTX_LAST_30D_START  = "last30dStart";
    public static final String CTX_LAST_30D_END    = "last30dEnd";

    @Override
    public void beforeJob(JobExecution jobExecution) {
        ExecutionContext ctx = jobExecution.getExecutionContext();
        if (ctx.containsKey(CTX_ANCHOR_DATE_KEY)) {
            return;
        }

        String anchorDateParam = jobExecution.getJobParameters().getString(PARAM_ANCHOR_DATE);
        RollingWindow window = RollingWindowResolver.resolve(anchorDateParam);

        ctx.putString(CTX_ANCHOR_DATE_KEY, window.anchorDateKey());
        ctx.putString(CTX_LAST_7D_START,   window.last7dStart().toString());
        ctx.putString(CTX_LAST_7D_END,     window.last7dEnd().toString());
        ctx.putString(CTX_LAST_30D_START,  window.last30dStart().toString());
        ctx.putString(CTX_LAST_30D_END,    window.last30dEnd().toString());
    }
}
