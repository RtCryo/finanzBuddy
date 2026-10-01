package com.finanzbuddy.api.auth.port.out;

public interface PasswordHasherPort {

    String hash(String rawPassword);
}
