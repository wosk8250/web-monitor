package com.webmonitor.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AlertBulkPatchRequest {
    private List<Long> ids;
    private Boolean sent;
}
