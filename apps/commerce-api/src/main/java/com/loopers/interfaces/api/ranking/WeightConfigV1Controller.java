package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.WeightConfigService;
import com.loopers.domain.ranking.WeightConfig;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api-admin/v1/ranking/weights")
@RequiredArgsConstructor
public class WeightConfigV1Controller {

    private final WeightConfigService weightConfigService;

    // Query

    @GetMapping
    public ApiResponse<List<WeightConfigV1Dto.Response>> getAll() {
        List<WeightConfig> configs = weightConfigService.getAllActive();
        List<WeightConfigV1Dto.Response> responses = configs.stream()
                .map(WeightConfigV1Dto.Response::from)
                .toList();
        return ApiResponse.success(responses);
    }

    // Command

    @PostMapping
    public ApiResponse<WeightConfigV1Dto.Response> create(@RequestBody WeightConfigV1Dto.CreateRequest request) {
        WeightConfig config = weightConfigService.create(
                request.groupName(), request.wView(), request.wLike(), request.wOrder(), request.trafficPct());
        return ApiResponse.success(WeightConfigV1Dto.Response.from(config));
    }

    @PutMapping("/{groupName}")
    public ApiResponse<WeightConfigV1Dto.Response> update(
            @PathVariable String groupName,
            @RequestBody WeightConfigV1Dto.UpdateRequest request) {
        WeightConfig config = weightConfigService.update(
                groupName, request.wView(), request.wLike(), request.wOrder(), request.trafficPct());
        return ApiResponse.success(WeightConfigV1Dto.Response.from(config));
    }

    @DeleteMapping("/{groupName}")
    public ApiResponse<Void> deactivate(@PathVariable String groupName) {
        weightConfigService.deactivate(groupName);
        return ApiResponse.success();
    }
}
