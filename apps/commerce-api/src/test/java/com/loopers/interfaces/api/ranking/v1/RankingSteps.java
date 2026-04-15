package com.loopers.interfaces.api.ranking.v1;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.loopers.interfaces.api.ApiResponse;

public class RankingSteps {

    private static final String DAILY_ENDPOINT = "/api/v1/rankings/daily";
    private static final String HOURLY_ENDPOINT = "/api/v1/rankings/hourly";
    private static final String WEEKLY_ENDPOINT = "/api/v1/rankings/weekly";

    public static ResponseEntity<ApiResponse<RankingDto.RankingResponse>> getDailyRankings(
            TestRestTemplate testRestTemplate,
            String queryParams
    ) {
        return doGet(testRestTemplate, DAILY_ENDPOINT, queryParams);
    }

    public static ResponseEntity<ApiResponse<RankingDto.RankingResponse>> getHourlyRankings(
            TestRestTemplate testRestTemplate,
            String queryParams
    ) {
        return doGet(testRestTemplate, HOURLY_ENDPOINT, queryParams);
    }

    public static ResponseEntity<ApiResponse<RankingDto.RankingResponse>> getWeeklyRankings(
            TestRestTemplate testRestTemplate,
            String queryParams
    ) {
        return doGet(testRestTemplate, WEEKLY_ENDPOINT, queryParams);
    }

    private static ResponseEntity<ApiResponse<RankingDto.RankingResponse>> doGet(
            TestRestTemplate testRestTemplate,
            String endpoint,
            String queryParams
    ) {
        ParameterizedTypeReference<ApiResponse<RankingDto.RankingResponse>> responseType =
                new ParameterizedTypeReference<>() {};
        String url = queryParams.isEmpty() ? endpoint : endpoint + "?" + queryParams;
        return testRestTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(null),
                responseType
        );
    }
}
