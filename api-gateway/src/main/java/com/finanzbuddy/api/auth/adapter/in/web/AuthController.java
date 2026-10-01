package com.finanzbuddy.api.auth.adapter.in.web;

import com.finanzbuddy.api.auth.adapter.in.web.dto.AuthResponse;
import com.finanzbuddy.api.auth.adapter.in.web.dto.LoginRequest;
import com.finanzbuddy.api.auth.adapter.in.web.dto.RegisterRequest;
import com.finanzbuddy.api.auth.domain.model.AccessToken;
import com.finanzbuddy.api.auth.domain.model.Credentials;
import com.finanzbuddy.api.auth.domain.model.Registration;
import com.finanzbuddy.api.auth.port.in.LoginUseCase;
import com.finanzbuddy.api.auth.port.in.RegisterUserUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;

    public AuthController(RegisterUserUseCase registerUserUseCase, LoginUseCase loginUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AccessToken token =
                registerUserUseCase.register(new Registration(request.email(), request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token.value()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AccessToken token =
                loginUseCase.login(new Credentials(request.email(), request.password()));
        return ResponseEntity.ok(new AuthResponse(token.value()));
    }
}
