package com.jeonlog.exhibition_recommender.exhibition.controller;

import com.jeonlog.exhibition_recommender.auth.annotation.CurrentUser;
import com.jeonlog.exhibition_recommender.common.api.ApiResponse;
import com.jeonlog.exhibition_recommender.exhibition.dto.CategoryCountDto;
import com.jeonlog.exhibition_recommender.exhibition.dto.ClickLogCreateResponse;
import com.jeonlog.exhibition_recommender.exhibition.dto.ExhibitionClickLogDto;
import com.jeonlog.exhibition_recommender.exhibition.service.ExhibitionClickLogService;
import com.jeonlog.exhibition_recommender.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exhibitions")
public class ExhibitionClickLogController {

    private final ExhibitionClickLogService clickLogService;

    @PostMapping("/{id}/click")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ClickLogCreateResponse>> saveClickLog(
            @PathVariable("id") Long exhibitionId,
            @CurrentUser User user,
            @RequestBody(required = false) ExhibitionClickLogDto requestDto
    ) {
        ExhibitionClickLogService.SavedClick saved = clickLogService.saveClick(exhibitionId, user, requestDto);

        ClickLogCreateResponse body = ClickLogCreateResponse.builder()
                .logId(saved.getLogId())
                .exhibitionId(exhibitionId)
                .userId(user.getId())
                .clickedAt(saved.getClickedAt().toString())
                .build();

        return ResponseEntity
                .created(URI.create("/api/exhibitions/" + exhibitionId + "/click-logs/" + body.getLogId()))
                .body(ApiResponse.ok(body));
    }

    @GetMapping("/{id}/click-stats/age-group")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CategoryCountDto>>> getClickStatsByAgeGroupForExhibition(
            @PathVariable("id") Long exhibitionId) {
        List<CategoryCountDto> stats = clickLogService.getClickStatsByAgeGroupForExhibition(exhibitionId);
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    @GetMapping("/{id}/click-stats/gender")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CategoryCountDto>>> getClickStatsByGenderForExhibition(
            @PathVariable("id") Long exhibitionId) {
        List<CategoryCountDto> stats = clickLogService.getClickStatsByGenderForExhibition(exhibitionId);
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}
