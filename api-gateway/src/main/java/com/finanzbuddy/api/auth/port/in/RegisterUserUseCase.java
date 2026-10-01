package com.finanzbuddy.api.auth.port.in;

import com.finanzbuddy.api.auth.domain.model.AccessToken;
import com.finanzbuddy.api.auth.domain.model.Registration;

public interface RegisterUserUseCase {

    AccessToken register(Registration registration);
}
