package com.loopers.batch.job.ranking.validator;

import com.loopers.batch.domain.ranking.MonthRange;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.stereotype.Component;

@Component
public class MonthlyJobParametersValidator implements JobParametersValidator {

    static final String PARAM_YEAR_MONTH = "year_month";

    @Override
    public void validate(JobParameters parameters) throws JobParametersInvalidException {
        if (parameters == null) {
            throw new JobParametersInvalidException("JobParameters must not be null");
        }
        String yearMonth = parameters.getString(PARAM_YEAR_MONTH);
        if (yearMonth == null || yearMonth.isBlank()) {
            throw new JobParametersInvalidException(
                "required JobParameter missing: " + PARAM_YEAR_MONTH);
        }
        try {
            MonthRange.of(yearMonth);
        } catch (IllegalArgumentException e) {
            throw new JobParametersInvalidException(
                "invalid " + PARAM_YEAR_MONTH + "=" + yearMonth + " (" + e.getMessage() + ")");
        }
    }
}
