package com.tongtin.dashboard.web;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.dashboard.dto.HostDashboardResponse;
import com.tongtin.dashboard.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/host")
@PreAuthorize("hasRole('HOST')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public HostDashboardResponse dashboard(@AuthenticationPrincipal AuthPrincipal principal) {
        return dashboardService.dashboard(principal.ownerId());
    }
}