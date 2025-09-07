package com.vijay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderInfo {
    private String name;
    private String displayName;
    private String description;
    private List<String> availableModels;
    private boolean isAvailable;
    private String status;
}
