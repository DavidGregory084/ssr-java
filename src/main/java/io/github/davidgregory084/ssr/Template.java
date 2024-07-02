package io.github.davidgregory084.ssr;

import java.util.Map;

@FunctionalInterface
public interface Template {
    String render(Map<String, String> attrs);
}
