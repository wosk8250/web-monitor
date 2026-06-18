package com.webmonitor.exception.resource;

public class SettingNotFoundException extends ResourceNotFoundException {

    private static final String ERROR_CODE = "RESOURCE_005";

    public SettingNotFoundException(Long settingId) {
        super(String.format("설정을 찾을 수 없습니다. (ID: %d)", settingId), ERROR_CODE);
        addContext("settingId", settingId);
    }

    public SettingNotFoundException(String message) {
        super(message, ERROR_CODE);
    }
}
