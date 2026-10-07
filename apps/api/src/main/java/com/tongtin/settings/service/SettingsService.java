package com.tongtin.settings.service;

import com.tongtin.common.errors.BadRequestException;
import com.tongtin.identity.service.AuditService;
import com.tongtin.settings.SettingDefinition;
import com.tongtin.settings.SettingType;
import com.tongtin.settings.SettingsCatalog;
import com.tongtin.settings.dto.SettingCategoryDto;
import com.tongtin.settings.dto.SettingDto;
import com.tongtin.settings.dto.SettingsViewDto;
import com.tongtin.settings.entity.SystemSetting;
import com.tongtin.settings.repository.SystemSettingRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single read/write path for every runtime setting. Reads are fail-safe: a
 * missing row or a malformed stored value falls back to the catalog default
 * so bad configuration can never break a request. Writes validate against the
 * catalog and are audited with the list of changed keys.
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final SystemSettingRepository repository;
    private final AuditService auditService;

    public SettingsService(SystemSettingRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    // ---------- typed reads ----------

    public String getString(String key, String fallback) {
        String raw = effectiveValue(key);
        return raw != null ? raw : fallback;
    }

    public boolean getBoolean(String key, boolean fallback) {
        String raw = effectiveValue(key);
        if (raw == null) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        log.warn("Setting {} has non-boolean value '{}'; using default {}", key, raw, fallback);
        return fallback;
    }

    public int getInt(String key, int fallback) {
        String raw = effectiveValue(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            log.warn("Setting {} has non-integer value '{}'; using default {}", key, raw, fallback);
            return fallback;
        }
    }

    public List<Integer> getIntList(String key, List<Integer> fallback) {
        String raw = effectiveValue(key);
        if (raw == null) {
            return fallback;
        }
        try {
            List<Integer> parsed = parseIntList(raw);
            return parsed.isEmpty() ? fallback : parsed;
        } catch (NumberFormatException ex) {
            log.warn("Setting {} has invalid number list '{}'; using default {}", key, raw, fallback);
            return fallback;
        }
    }

    private String effectiveValue(String key) {
        try {
            return repository.findById(key)
                    .map(SystemSetting::getValue)
                    .filter(value -> !value.isBlank())
                    .orElseGet(() -> SettingsCatalog.byKey(key)
                            .map(SettingDefinition::defaultValue)
                            .orElse(null));
        } catch (RuntimeException ex) {
            log.warn("Failed to read setting {}: {}", key, ex.getMessage());
            return SettingsCatalog.byKey(key).map(SettingDefinition::defaultValue).orElse(null);
        }
    }

    // ---------- admin view ----------

    @Transactional(readOnly = true)
    public SettingsViewDto getView() {
        Map<String, SystemSetting> stored = new LinkedHashMap<>();
        for (SystemSetting row : repository.findAll()) {
            stored.put(row.getKey(), row);
        }

        List<SettingCategoryDto> categories = new ArrayList<>();
        for (SettingsCatalog.Category category : SettingsCatalog.categories()) {
            List<SettingDto> settings = new ArrayList<>();
            for (SettingDefinition definition : category.settings()) {
                settings.add(toDto(definition, stored.get(definition.key())));
            }
            categories.add(new SettingCategoryDto(category.code(), category.label(), settings));
        }
        return new SettingsViewDto(categories);
    }

    private SettingDto toDto(SettingDefinition definition, SystemSetting row) {
        boolean configured = row != null && row.getValue() != null && !row.getValue().isBlank();
        boolean secret = definition.type() == SettingType.SECRET;
        String value = secret ? null : (configured ? row.getValue() : definition.defaultValue());
        Instant updatedAt = row != null ? row.getUpdatedAt() : null;
        return new SettingDto(
                definition.key(),
                definition.label(),
                definition.description(),
                definition.type().name(),
                value,
                configured,
                secret ? null : definition.defaultValue(),
                definition.min(),
                definition.max(),
                updatedAt);
    }

    // ---------- bulk update ----------

    @Transactional
    public SettingsViewDto update(Long actorUserId, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            throw new BadRequestException("Không có cấu hình nào được gửi lên");
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            SettingDefinition definition = SettingsCatalog.byKey(entry.getKey())
                    .orElseThrow(() -> new BadRequestException("Cấu hình không tồn tại: " + entry.getKey()));
            String value = normalize(definition, entry.getValue());
            if (value != null) {
                normalized.put(definition.key(), value);
            }
        }

        Set<String> changedKeys = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            String key = entry.getKey();
            String storedValue = repository.findById(key).map(SystemSetting::getValue).orElse(null);
            if (entry.getValue().equals(storedValue)) {
                continue;
            }
            SettingDefinition definition = SettingsCatalog.byKey(key).orElseThrow();
            SystemSetting row = repository.findById(key)
                    .orElseGet(() -> new SystemSetting(key, entry.getValue(), definition.description()));
            row.setValue(entry.getValue());
            if (row.getDescription() == null) {
                row.setDescription(definition.description());
            }
            repository.save(row);
            changedKeys.add(key);
        }

        if (!changedKeys.isEmpty()) {
            auditService.record(actorUserId, "SystemSetting", "settings", "ADMIN_UPDATE_SETTINGS",
                    Map.<String, Object>of("changedKeys", new ArrayList<>(changedKeys)));
        }
        return getView();
    }

    private String normalize(SettingDefinition definition, String rawValue) {
        if (definition.type() == SettingType.SECRET) {
            // Blank secret means "keep the stored one".
            return rawValue == null || rawValue.isBlank() ? null : rawValue.trim();
        }
        if (rawValue == null || rawValue.trim().isEmpty()) {
            throw new BadRequestException("Giá trị không được để trống: " + definition.key());
        }
        String value = rawValue.trim();
        return switch (definition.type()) {
            case BOOLEAN -> {
                String lowered = value.toLowerCase(Locale.ROOT);
                if (!"true".equals(lowered) && !"false".equals(lowered)) {
                    throw new BadRequestException("Giá trị true/false không hợp lệ: " + definition.key());
                }
                yield lowered;
            }
            case INT -> String.valueOf(parseInRange(definition, value));
            case INT_LIST -> {
                List<Integer> items;
                try {
                    items = parseIntList(value);
                } catch (NumberFormatException ex) {
                    throw new BadRequestException("Danh sách số không hợp lệ: " + definition.key());
                }
                if (items.isEmpty()) {
                    throw new BadRequestException("Danh sách số không được để trống: " + definition.key());
                }
                for (Integer item : items) {
                    checkRange(definition, item);
                }
                yield items.stream()
                        .distinct()
                        .sorted(Comparator.reverseOrder())
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
            }
            case URL -> {
                if (!value.startsWith("http://") && !value.startsWith("https://")) {
                    throw new BadRequestException("Địa chỉ phải bắt đầu bằng http:// hoặc https://: " + definition.key());
                }
                yield value;
            }
            default -> value;
        };
    }

    private int parseInRange(SettingDefinition definition, String value) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Giá trị số không hợp lệ: " + definition.key());
        }
        checkRange(definition, parsed);
        return parsed;
    }

    private void checkRange(SettingDefinition definition, int value) {
        if (definition.min() != null && value < definition.min()) {
            throw new BadRequestException(definition.key() + " phải lớn hơn hoặc bằng " + definition.min());
        }
        if (definition.max() != null && value > definition.max()) {
            throw new BadRequestException(definition.key() + " phải nhỏ hơn hoặc bằng " + definition.max());
        }
    }

    private List<Integer> parseIntList(String raw) {
        List<Integer> parsed = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parsed.add(Integer.parseInt(trimmed));
            }
        }
        return parsed;
    }
}
