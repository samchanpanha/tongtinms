package com.tongtin.settings.dto;

import java.util.Map;

public record UpdateSettingsRequest(Map<String, String> values) {
}
