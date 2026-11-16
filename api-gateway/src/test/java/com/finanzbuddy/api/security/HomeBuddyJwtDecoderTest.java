package com.finanzbuddy.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class HomeBuddyJwtDecoderTest {

    @Test
    void acceptsHomeBuddyRsaTokensAndRefreshesJwksForNewKey() throws Exception {
        RSAKey first = key("first");
        RSAKey second = key("second");
        AtomicReference<JWKSet> published = new AtomicReference<>(new JWKSet(first.toPublicJWK()));
        HttpServer server = server(published);
        try {
            JwtDecoder decoder = decoder(server);
            assertThat(decoder.decode(token(first, "homebuddy-auth", "finanzbuddy")).getSubject())
                    .isNotBlank();
            published.set(new JWKSet(List.of(first.toPublicJWK(), second.toPublicJWK())));
            assertThat(decoder.decode(token(second, "homebuddy-auth", "finanzbuddy")).getSubject())
                    .isNotBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsWrongIssuerAudienceAndLegacyHmac() throws Exception {
        RSAKey key = key("current");
        HttpServer server = server(new AtomicReference<>(new JWKSet(key.toPublicJWK())));
        try {
            JwtDecoder decoder = decoder(server);
            assertThatThrownBy(() -> decoder.decode(token(key, "other", "finanzbuddy")))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> decoder.decode(token(key, "homebuddy-auth", "calendarbuddy")))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> decoder.decode(hmacToken())).isInstanceOf(Exception.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsTokensWithoutAudienceOrExpiryAsInvalidTokens() throws Exception {
        RSAKey key = key("current");
        HttpServer server = server(new AtomicReference<>(new JWKSet(key.toPublicJWK())));
        try {
            JwtDecoder decoder = decoder(server);
            JWTClaimsSet noAudience =
                    new JWTClaimsSet.Builder(claims("homebuddy-auth", "finanzbuddy"))
                            .audience((String) null)
                            .build();
            JWTClaimsSet noExpiry =
                    new JWTClaimsSet.Builder(claims("homebuddy-auth", "finanzbuddy"))
                            .expirationTime(null)
                            .build();
            assertThatThrownBy(() -> decoder.decode(sign(key, noAudience)))
                    .isInstanceOf(JwtValidationException.class);
            assertThatThrownBy(() -> decoder.decode(sign(key, noExpiry)))
                    .isInstanceOf(JwtValidationException.class);
        } finally {
            server.stop(0);
        }
    }

    private RSAKey key(String kid) throws Exception {
        return new RSAKeyGenerator(2048).keyID(kid).generate();
    }

    private JwtDecoder decoder(HttpServer server) {
        return SecurityConfig.homeBuddyDecoder(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks",
                "homebuddy-auth",
                ObservationRegistry.NOOP);
    }

    @Test
    void jwksFetchCarriesTheTraceOfTheValidatingRequest() throws Exception {
        RSAKey key = key("traced");
        AtomicReference<String> traceparent = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/jwks",
                exchange -> {
                    traceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
                    byte[] body =
                            new JWKSet(key.toPublicJWK())
                                    .toString()
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                });
        server.start();
        SdkTracerProvider provider = SdkTracerProvider.builder().build();
        try {
            OpenTelemetrySdk openTelemetry =
                    OpenTelemetrySdk.builder()
                            .setTracerProvider(provider)
                            .setPropagators(
                                    ContextPropagators.create(
                                            W3CTraceContextPropagator.getInstance()))
                            .build();
            io.opentelemetry.api.trace.Tracer otelTracer = openTelemetry.getTracer("test");
            Tracer tracer = new OtelTracer(otelTracer, new OtelCurrentTraceContext(), event -> {});
            ObservationRegistry registry = ObservationRegistry.create();
            registry.observationConfig()
                    .observationHandler(
                            new ObservationHandler.FirstMatchingCompositeObservationHandler(
                                    new PropagatingSenderTracingObservationHandler<>(
                                            tracer,
                                            new OtelPropagator(
                                                    openTelemetry.getPropagators(), otelTracer)),
                                    new DefaultTracingObservationHandler(tracer)));
            JwtDecoder decoder =
                    SecurityConfig.homeBuddyDecoder(
                            "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks",
                            "homebuddy-auth",
                            registry);

            Observation request = Observation.start("request", registry);
            try (Observation.Scope ignored = request.openScope()) {
                decoder.decode(token(key, "homebuddy-auth", "finanzbuddy"));
            } finally {
                request.stop();
            }

            TracingObservationHandler.TracingContext tracing =
                    request.getContext()
                            .getRequired(TracingObservationHandler.TracingContext.class);
            String traceId = tracing.getSpan().context().traceId();
            assertThat(traceparent.get()).startsWith("00-" + traceId + "-");
        } finally {
            server.stop(0);
            provider.close();
        }
    }

    private HttpServer server(AtomicReference<JWKSet> published) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/jwks",
                exchange -> {
                    byte[] body = published.get().toString().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                });
        server.start();
        return server;
    }

    private String token(RSAKey key, String issuer, String audience) throws Exception {
        return sign(key, claims(issuer, audience));
    }

    private String sign(RSAKey key, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                        claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private String hmacToken() throws Exception {
        SignedJWT jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("old").build(),
                        claims("homebuddy-auth", "finanzbuddy"));
        jwt.sign(new MACSigner(new byte[32]));
        return jwt.serialize();
    }

    private JWTClaimsSet claims(String issuer, String audience) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .audience(audience)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .claim("email", "person@example.com")
                .build();
    }
}
