package com.finanzbuddy.api.document.dto;

import java.util.List;

public record TransactionEditResponse(TransactionResponse transaction, List<TransactionTag> tags) {}
