package io.github.davidgregory084.ssr;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.xmlunit.matchers.CompareMatcher.isSimilarTo;

class RendererTest {
    @Test
    void showsDefaultContentInEmptyUnnamedSlot() {
        var renderer = new Renderer(
                true,
                Map.of("my-button", attrs -> """
                        <button>
                          <slot>Submit</slot>
                        </button>
                        """)
        );

        var actual = renderer.render("""
                <my-button></my-button>
                """);

        var expected = """
                <my-button>
                  <button>Submit</button>
                </my-button>
                """;

        assertThat(actual, isSimilarTo(expected).ignoreWhitespace());
    }

    @Test
    void whitespaceCountsAsContentInUnnamedSlot() {
        var renderer = new Renderer(
                true,
                Map.of("my-button", attrs -> """
                        <button>
                          <slot>Submit</slot>
                        </button>
                        """)
        );

        var actual = renderer.render("""
                <my-button> </my-button>
                """);

        var expected = """
                <my-button>
                  <button></button>
                </my-button>
                """;

        assertThat(actual, isSimilarTo(expected).ignoreWhitespace());
    }

    @Test
    void replacesContentInUnnamedSlot() {
        var renderer = new Renderer(
                true,
                Map.of("my-button", attrs -> """
                        <button>
                          <slot>Submit</slot>
                        </button>
                        """)
        );

        var actual = renderer.render("""
                <my-button>Let's Go!</my-button>
                """);

        var expected = """
                <my-button>
                  <button>Let's Go!</button>
                </my-button>
                """;

        assertThat(actual, isSimilarTo(expected).ignoreWhitespace());
    }

    @Test
    void expandsTemplate() {
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

        var actual = renderer.render("""
                <my-paragraph></my-paragraph>
                """);

        var expected = """
                <my-paragraph>
                  <p><span slot="my-text">My default text</span></p>
                </my-paragraph>
                """;

        assertThat(actual, isSimilarTo(expected).ignoreWhitespace());
    }

    @Test
    void expandsTemplateWithNamedSlot() {
        var renderer = new Renderer(
                true,
                Map.of("my-multiples", attrs -> """
                        <slot name="my-content" as="div">
                          My default text
                          <h3>A smaller heading</h3>
                          Random text
                          <code> a code block</code>
                        </slot>
                        """)
        );

        var actual = renderer.render("""
                <my-multiples></my-multiples>
                """);

        var expected = """
                <my-multiples>
                  <div slot="my-content">
                    My default text
                    <h3>A smaller heading</h3>
                    Random text
                    <code>a code block</code>
                  </div>
                </my-multiples>""";

        assertThat(actual, isSimilarTo(expected).ignoreWhitespace());
    }

    @Test
    void replacesNamedSlotWithInsert() {
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

        var actual = renderer.render("""
                <my-paragraph>
                  <span slot="my-text">Slotted</span>
                </my-paragraph>
                """);

        var expected = """
                <my-paragraph>
                  <p><span slot="my-text">Slotted</span></p>
                </my-paragraph>
                """;

        assertThat(actual, isSimilarTo(expected).ignoreWhitespace());
    }

    @Test
    void addsUnslottedChildrenToUnnamedSlot() {
        var renderer = new Renderer(
                true,
                Map.of("my-content", attrs -> """
                        <h2>My Content</h2>
                        <slot name="title">
                          <h3>
                            Title
                          </h3>
                        </slot>
                        <slot></slot>
                        """)
        );

        var actual = renderer.render("""
                <my-content id="0">
                  <h4 slot="title">Custom title</h4>
                </my-content>
                """);

        var expected = """
                <my-content id="0">
                  <h2>My Content</h2>
                  <h4 slot="title">Custom title</h4>
                </my-content>
                """;

        assertThat(actual, isSimilarTo(expected).ignoreWhitespace());
    }

    @Test
    void replacesNestedSlots() {
        var renderer = new Renderer(
                true,
                Map.of("my-content", attrs -> """
                        <h2>My Content</h2>
                        <slot name="title">
                          <h3>
                            Title
                          </h3>
                        </slot>
                        <slot></slot>
                        """)
        );

        var actual = renderer.render("""
                <my-content>
                  <my-content id="0">
                    <h3 slot="title">Second</h3>
                    <my-content id="1">
                      <h3 slot="title">Third</h3>
                    </my-content>
                  </my-content>
                </my-content>
                """);

        var expected = """
             <my-content>
                <h2>My Content</h2>
                <h3 slot="title">
                  Title
                </h3>
                <my-content id="0">
                  <h2>My Content</h2>
                  <h3 slot="title">Second</h3>
                <my-content id="1">
                   <h2>My Content</h2>
                   <h3 slot="title">Third</h3>
                 </my-content>
                </my-content>
             </my-content>
             """;

        assertThat(actual, isSimilarTo(expected).ignoreWhitespace());
    }

    @Test
    void respectsAsAttribute() {
        var renderer = new Renderer(
                true,
                Map.of("my-slot-as", attrs -> """
                        <slot as="div" name="stuff">
                          stuff
                        </slot>
                        """)
        );

        var actual = renderer.render("""
                <my-slot-as></my-slot-as>
                """);

        var expected = """
                <my-slot-as>
                  <div slot="stuff">
                    stuff
                  </div>
                </my-slot-as>
                """;

        assertThat(actual, isSimilarTo(expected).ignoreWhitespace());
    }

    @Test
    void rendersNestedCustomElements() {
        var renderer = new Renderer(
                true,
                Map.of("my-heading", attrs -> """
                        <h1>
                          <slot></slot>
                        </h1>
                        """,
                        "my-super-heading", attrs -> """
                        <slot name="emoji"></slot>
                        <my-heading>
                          <slot></slot>
                        </my-heading>
                        """)
        );

        var actual = renderer.render("""
                <my-super-heading>
                  <span slot="emoji">
                    ✨
                  </span>
                  My Heading
                </my-super-heading>
                """);

        var expected = """
                <my-super-heading>
                  <span slot="emoji">
                    ✨
                  </span>
                  <my-heading>
                    <h1>
                      My Heading
                    </h1>
                  </my-heading>
                </my-super-heading>
                """;

        assertThat(actual, isSimilarTo(expected).ignoreWhitespace());
    }
}
