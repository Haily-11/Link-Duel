package com.woner.linkgame.controller;

import com.woner.linkgame.dto.RankingEntry;
import com.woner.linkgame.service.RankingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/top")
    public ResponseEntity<Map<String, Object>> top(@RequestParam(defaultValue = "10") int n) {
        List<RankingEntry> data = rankingService.top(Math.max(1, Math.min(n, 100)));
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }
}