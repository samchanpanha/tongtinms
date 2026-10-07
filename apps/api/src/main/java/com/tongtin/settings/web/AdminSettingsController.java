package com.tongtin.settings.web;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.settings.dto.SettingsViewDto;
import com.tongtin.settings.dto.UpdateSettingsRequest;
import com.tongtin.settings.service.SettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin control surface for every module's runtime configuration. The catalog
 * drives both this API and the admin UI, so a new setting only needs a catalog
 * entry to become manageable.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettingsController {

    private final SettingsService settingsService;

    public AdminSettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public SettingsViewDto getSettings() {
        return settingsService.getView();
    }

    @PutMapping
    public SettingsViewDto updateSettings(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody UpdateSettingsRequest request) {
        return settingsService.update(principal.userId(), request.values());
    }
}
