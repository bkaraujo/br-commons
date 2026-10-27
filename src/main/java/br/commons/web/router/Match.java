package br.commons.web.router;

import org.jspecify.annotations.NullMarked;

import java.util.Map;

@NullMarked
public record Match(
        Route route,
        Map<String, String> pathParams
) {}
