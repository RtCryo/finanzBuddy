package com.finanzbuddy.api.document.dto;

import java.util.List;

public record TransactionEditRequest(String category, String newCategoryName, List<TagSelection> tags) {
    public record TagSelection(Long id, String name) {}
}
