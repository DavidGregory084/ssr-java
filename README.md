# ssr-java

This is a prototypical Java port of [enhance-ssr](https://github.com/enhance-dev/enhance-ssr), which enables server-side rendering of custom elements using the algorithm described in [Portable Server Rendered Components with Enhance SSR](https://begin.com/blog/posts/2024-05-03-portable-ssr-components#how-does-it-work%3F).

## Usage

To use this library, you must create a `io.github.davidgregory084.ssr.Renderer` using a `Map` of custom element names to `io.github.davidgregory084.ssr.Template`s.

A `Template` is any function from `attributes: Map<String, String>` to `String`, which enables most common templating engines to be used for rendering.

```java
var renderer = new Renderer(
    true,
    Map.of("my-paragraph", attrs -> """
    <p>
      <slot name="my-text">
        My default text
      </slot>
    </p>
    """)
);

renderer.render("""
    <my-paragraph></my-paragraph>
    """);

// <p>
//   <span slot="my-text">
//     My default text
//   </span>
// </p>

renderer.render("""
    <my-paragraph><span slot="my-text">My slot text</span></my-paragraph>
    """);

// <p>
//   <span slot="my-text">
//     My slot text
//   </slot>
// </p>
```

## License

All code in this repository is licensed under the Apache License, Version 2.0. See [LICENSE](./LICENSE).
