package com.loopers.interfaces.api.ranking.v1;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.loopers.interfaces.api.ApiResponse;

public class RankingSteps {

    private static final String RANKING_ENDPOINT = "/api/v1/rankings";

    public static ResponseEntity<ApiResponse<RankingDto.RankingResponse>> getRankings(
            TestRestTemplate testRestTemplate,
            String queryParams
    ) {
        ParameterizedTypeReference<ApiResponse<RankingDto.RankingResponse>> responseType =
                new ParameterizedTypeReference<>() {};
        String url = queryParams.isEmpty() ? RANKING_ENDPOINT : RANKING_ENDPOINT + "?" + queryParams;
        return testRestTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(null),
                responseType
        );
    }
}
