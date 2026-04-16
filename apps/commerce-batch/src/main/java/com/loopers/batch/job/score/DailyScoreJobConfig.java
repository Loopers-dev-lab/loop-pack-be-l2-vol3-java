package com.loopers.batch.job.score;

import com.loopers.batch.job.score.step.DailyScoreProcessor;
import com.loopers.batch.job.score.step.MvProductScoreDailyRow;
import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.ranking.ScoreCalculator;
import com.loopers.domain.signal.ProductDailySignalModel;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = DailyScoreJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class DailyScoreJobConfig {

    public static final String JOB_NAME = "dailyScoreJob";
    private static final String STEP_NAME = "buildDailyScoreStep";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final DataSource dataSource;
    private final ScoreCalculator scoreCalculator;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ChunkListener chunkListener;

    @Value("${batch.daily-score.chunk-size:5000}")
    private int chunkSize;

    @Bean(JOB_NAME)
    public Job dailyScoreJob(Step buildDailyScoreStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobListener)
                .start(buildDailyScoreStep)
                .build();
    }

    @Bean(STEP_NAME)
    @JobScope
    public Step buildDailyScoreStep(
            @Value("#{jobParameters['date']}") String dateStr
    ) {
        LocalDate date = LocalDate.parse(dateStr, DATE_FMT);
        return new StepBuilder(STEP_NAME, jobRepository)
                .<ProductDailySignalModel, MvProductScoreDailyRow>chunk(chunkSize, transactionManager)
                .reader(dailySignalReader(date))
                .processor(dailyScoreProcessor(date))
                .writer(scoreDailyWriter())
                .listener(stepMonitorListener)
                .listener(chunkListener)
                .build();
    }

    private JpaPagingItemReader<ProductDailySignalModel> dailySignalReader(LocalDate date) {
        JpaPagingItemReader<ProductDailySignalModel> reader = new JpaPagingItemReader<>();
        reader.setEntityManagerFactory(entityManagerFactory);
        reader.setQueryString(
                "SELECT p FROM ProductDailySignalModel p WHERE p.signalDate = :signalDate ORDER BY p.productDbId"
        );
        reader.setParameterValues(Map.of("signalDate", date));
        reader.setPageSize(chunkSize);
        reader.setName("dailySignalReader");
        return reader;
    }

    private ItemProcessor<ProductDailySignalModel, MvProductScoreDailyRow> dailyScoreProcessor(LocalDate date) {
        return new DailyScoreProcessor(scoreCalculator, date);
    }

    private JdbcBatchItemWriter<MvProductScoreDailyRow> scoreDailyWriter() {
        return new JdbcBatchItemWriterBuilder<MvProductScoreDailyRow>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO mv_product_score_daily
                            (product_db_id, score_date, score, view_count, like_count, order_amount, created_at, updated_at)
                        VALUES
                            (?, ?, ?, ?, ?, ?, NOW(), NOW())
                        ON DUPLICATE KEY UPDATE
                            score = VALUES(score),
                            view_count = VALUES(view_count),
                            like_count = VALUES(like_count),
                            order_amount = VALUES(order_amount),
                            updated_at = NOW()
                        """)
                .itemPreparedStatementSetter((row, ps) -> {
                    ps.setLong(1, row.productDbId());
                    ps.setObject(2, row.scoreDate());
                    ps.setDouble(3, row.score());
                    ps.setLong(4, row.viewCount());
                    ps.setLong(5, row.likeCount());
                    ps.setBigDecimal(6, row.orderAmount());
                })
                .build();
    }
}
