package com.tongtin.settings.dto;

import java.util.List;

public record SettingCategoryDto(String code, String label, List<SettingDto> settings) {
}
