package com.example.authengine.factor;

import com.example.authengine.common.ApiException;
import com.example.authengine.domain.FactorType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class FactorRegistry {
    private final Map<FactorType, FactorHandler> handlers = new EnumMap<>(FactorType.class);

    public FactorRegistry(List<FactorHandler> implementations) {
        for (FactorHandler h : implementations) {
            if (handlers.putIfAbsent(h.type(), h) != null)
                throw new IllegalStateException("认证器重复注册: " + h.type());
        }
    }

    public FactorHandler require(FactorType type) {
        FactorHandler handler = handlers.get(type);
        if (handler == null || !handler.available()) throw ApiException.bad("认证器不可用: " + type);
        return handler;
    }

    public Set<FactorType> installedTypes() {
        return handlers.values().stream().filter(FactorHandler::available)
                .map(FactorHandler::type).collect(Collectors.toUnmodifiableSet());
    }
}
