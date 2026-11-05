package com.finanzbuddy.api.dashboard.controller;

import com.finanzbuddy.api.dashboard.dto.DashboardResponse;
import com.finanzbuddy.api.dashboard.service.DashboardService;
import com.finanzbuddy.api.security.AccountPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(
            @AuthenticationPrincipal AccountPrincipal userDetails) {
        return ResponseEntity.ok(dashboardService.getDashboard(userDetails.userId()));
    }
}
