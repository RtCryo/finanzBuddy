package com.finanzbuddy.api.security.domain.model;

import java.util.List;

/** An authenticated subject and the authorities granted to it, free of any framework type. */
public record Principal(String username, List<String> authorities) {}
