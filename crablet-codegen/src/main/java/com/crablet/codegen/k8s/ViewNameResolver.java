package com.crablet.codegen.k8s;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Maps view spec names (PascalCase) to kebab-case view processor names for
 * {@code getViewName()} and {@code view_progress.view_name}.
 */
public final class ViewNameResolver {

    private static final Pattern CAMEL_HUMP = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Pattern ACRONYM_WORD = Pattern.compile("([A-Z]+)([A-Z][a-z])");

    private ViewNameResolver() {
    }

    public static String viewName(String specName) {
        if (specName == null || specName.isBlank()) {
            return "view";
        }
        String s = CAMEL_HUMP.matcher(specName).replaceAll("$1-$2");
        s = ACRONYM_WORD.matcher(s).replaceAll("$1-$2");
        return s.toLowerCase(Locale.ROOT);
    }
}
