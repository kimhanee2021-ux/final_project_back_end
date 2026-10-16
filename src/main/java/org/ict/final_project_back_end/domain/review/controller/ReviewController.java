package org.ict.final_project_back_end.domain.review.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.ict.final_project_back_end.domain.review.dto.ReviewDtos;
import org.ict.final_project_back_end.domain.review.service.ReviewService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/games/{appId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/summary")
    public ReviewDtos.Summary summary(@PathVariable @Positive long appId) {
        return reviewService.getSummary(appId);
    }

    @GetMapping
    public ReviewDtos.Page list(
            @PathVariable @Positive long appId,
            @RequestParam(defaultValue = "RECENT") ReviewDtos.Sort sort,
            @RequestParam(defaultValue = "ALL") ReviewDtos.Type type,
            @RequestParam(defaultValue = "all") @Pattern(regexp = "all|korean|english") String language,
            @RequestParam(defaultValue = "*") @Size(max = 200) String cursor,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size) {
        return reviewService.getReviews(appId, sort, type, language, cursor, size);
    }
}
