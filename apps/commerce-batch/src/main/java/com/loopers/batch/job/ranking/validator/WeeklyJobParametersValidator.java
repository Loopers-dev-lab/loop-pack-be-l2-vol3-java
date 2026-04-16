package com.loopers.batch.job.ranking.validator;

import com.loopers.batch.domain.ranking.WeekRange;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.stereotype.Component;

@Component
public class WeeklyJobParametersValidator implements JobParametersValidator {

    static final String PARAM_YEAR_WEEK = "year_week";

    @Override
    public void validate(JobParameters parameters) throws JobParametersInvalidException {
        if (parameters == null) {
            throw new JobParametersInvalidException("JobParameters must not be null");
        }
        String yearWeek = parameters.getString(PARAM_YEAR_WEEK);
        if (yearWeek == null || yearWeek.isBlank()) {
            throw new JobParametersInvalidException(
                "required JobParameter missing: " + PARAM_YEAR_WEEK);
        }
        try {
            WeekRange.of(yearWeek);
        } catch (IllegalArgumentException e) {
            throw new JobParametersInvalidException(
                "invalid " + PARAM_YEAR_WEEK + "=" + yearWeek + " (" + e.getMessage() + ")");
        }
    }
}
