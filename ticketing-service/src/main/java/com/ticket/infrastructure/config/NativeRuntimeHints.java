package com.ticket.infrastructure.config;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class NativeRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Đăng ký tài nguyên kịch bản Lua và Migration SQL cho GraalVM Native Image
        hints.resources().registerPattern("lua/*.lua");
        hints.resources().registerPattern("db/migration/*.sql");
    }
}
