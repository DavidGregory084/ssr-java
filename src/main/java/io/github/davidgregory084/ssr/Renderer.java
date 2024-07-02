package io.github.davidgregory084.ssr;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Renderer {
    /**
     * A Potential Custom Element Name character.
     *
     * @see <a href="https://html.spec.whatwg.org/multipage/custom-elements.html#valid-custom-element-name">Valid custom element name</a>
     */
    private static final String PCENChar = "[_.-0-9a-z\\xB7\\xC0-\\xD6\\xD8-\\xF6\\xF8-\\u037D\\u037F-\\u1FFF\\u200C-\\u200D\\u203F-\\u2040\\u2070-\\u218F\\u2C00-\\u2FEF\\u3001-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFFD\\x{10000}-\\x{EFFFF}]";
    /**
     * A {@link Pattern} for recognising a Potential Custom Element Name.
     *
     * @see <a href="https://html.spec.whatwg.org/multipage/custom-elements.html#valid-custom-element-name">Valid custom element name</a>
     */
    private static final Pattern customElement = Pattern.compile("[a-z]" + PCENChar + "*-" + PCENChar + "*");
    /**
     * Reserved element names which cannot be used for declaring a custom element.
     *
     * @see <a href="https://html.spec.whatwg.org/multipage/custom-elements.html#valid-custom-element-name">Valid custom element name</a>
     */
    private static final String[] reservedTags = new String[]{
            "annotation-xml",
            "color-profile",
            "font-face",
            "font-face-src",
            "font-face-uri",
            "font-face-format",
            "font-face-name",
            "missing-glyph)"
    };

    private static boolean isCustomElement(String tagName) {
        for (int i = 0; i < reservedTags.length; i++) {
            if (reservedTags[i].equals(tagName)) {
                return false;
            }
        }

        return customElement.matcher(tagName).matches();
    }

    private final boolean bodyContent;
    private final Map<String, Template> elements;

    public Renderer(
            boolean bodyContent,
            Map<String, Template> elements) {
        this.bodyContent = bodyContent;
        this.elements = elements;
    }

    private CustomElements processCustomElements(Element body) {
        CustomElements customElements = new CustomElements();

        if (body != null) {
            body.traverse((node, depth) -> {
                if (node instanceof Element) {
                    Element element = (Element) node;
                    if (isCustomElement(element.tagName())) {
                        if (elements.containsKey(element.tagName())) {
                            Template template = elements.get(element.tagName());
                            ExpandedTemplate expanded = expandTemplate(element, template);
                            customElements.collectedScripts().addAll(expanded.collectedScripts());
                            customElements.collectedStyles().addAll(expanded.collectedStyles());
                            customElements.collectedLinks().addAll(expanded.collectedLinks());
                            fillSlots(element, expanded);
                        }
                    }
                }
            });
        }

        return customElements;
    }

    private void fillSlots(Element element, ExpandedTemplate template) {
        List<Element> slots = findSlots(template);
        List<Element> inserts = findInserts(element);
        Set<Element> usedSlots = new HashSet<>();
        Set<Element> usedInserts = new HashSet<>();
        List<Element> unnamedSlots = new ArrayList<>();

        for (Element slot : slots) {
            if (!slot.hasAttr("name")) {
                unnamedSlots.add(slot);
                continue;
            }

            String slotName = slot.attr("name");
            for (Element insert : inserts) {
                String insertSlot = insert.attr("slot");
                if (insertSlot.equals(slotName)) {
                    slot.replaceWith(insert);
                    usedSlots.add(slot);
                    usedInserts.add(insert);
                }
            }
        }

        unnamedSlots.forEach(slot -> {
            List<Node> nodeChildren = element.childNodes().stream()
                    .filter(node -> !usedInserts.contains(node))
                    .collect(Collectors.toList());
            List<Node> children = nodeChildren.isEmpty() ?
                    slot.childNodes() : nodeChildren;
            Node preceding = slot;
            for (Node child : children) {
                preceding.after(child);
                preceding = child;
            }
            slot.remove();
        });

        List<Element> unusedSlots = slots.stream()
                .filter(slot -> !usedSlots.contains(slot))
                .collect(Collectors.toList());

        replaceSlots(element, unusedSlots);

        element.empty().appendChildren(template.fragment().childNodes());
    }

    private List<Element> findSlots(ExpandedTemplate template) {
        List<Element> elements = new ArrayList<>();

        template.fragment().traverse((node, depth) -> {
            if (node instanceof Element) {
                Element element = (Element) node;
                if (element.tagName().equals("slot")) {
                    elements.add(element);
                }
            }
        });

        return elements;
    }

    private List<Element> findInserts(Element target) {
        return target.children().stream()
                .filter(element -> element.hasAttr("slot"))
                .collect(Collectors.toList());
    }

    private void replaceSlots(Element element, List<Element> slots) {
        for (Element slot : slots) {
            String name = slot.attr("name");
            String asElement = slot.attr("as");
            boolean hasAsElement = slot.hasAttr("as");
            if (slot.hasAttr("name")) {
                List<Node> slotOriginalChildren = slot.childNodesCopy();
                List<Node> slotChildren = slot.childNodes().stream()
                        .filter(node -> !node.nodeName().startsWith("#"))
                        .collect(Collectors.toList());
                Element slotFirstChild = slot.firstElementChild();
                if (slotChildren.size() != 1) {
                    slot.empty()
                            .appendElement(hasAsElement ? asElement : "span")
                            .attr("slot", name)
                            .appendChildren(slotOriginalChildren);
                } else if (slotFirstChild != null) {
                    slotFirstChild.attr("slot", name);
                }

                Node preceding = slot;
                for (Node child : slot.childNodes()) {
                    preceding.after(child);
                    preceding = child;
                }
                slot.remove();
            }
        }
    }

    public String render(Path file, Charset cs) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            Document doc = Jsoup.parse(is, cs.name(), "");
            return render(doc);
        }
    }

    public String render(String input) {
        Document doc = Jsoup.parse(input);
        return render(doc);
    }

    private String render(Document doc) {
        Element body = doc.body();
        Element head = doc.head();
        CustomElements customElements = processCustomElements(body);

        // Collect unique scripts and append them to <body>
        Map<String, Element> uniqueScripts = new HashMap<>();
        customElements.collectedScripts().forEach(script -> {
            String scriptSrc = script.attr("src");
            String scriptContents = script.data();
            if (!scriptContents.isEmpty()) {
                uniqueScripts.put(scriptContents, script);
            } else if (!scriptSrc.isEmpty()) {
                uniqueScripts.put(scriptSrc, script);
            }
        });
        uniqueScripts.values().forEach(body::appendChild);

        // Merge unique styles together and append to <head>
        Set<String> uniqueStyles = new HashSet<>();
        customElements.collectedStyles().forEach(style -> {
            String styleData = style.data();
            uniqueStyles.add(styleData);
        });

        String mergedStyles = uniqueStyles.stream()
                .sorted((left, right) -> {
                    String leftData = left.trim();
                    String rightData = right.trim();
                    if (leftData.startsWith("@import") && !rightData.startsWith("@import")) return -1;
                    if (!leftData.startsWith("@import") && rightData.startsWith("@import")) return 1;
                    return 0;
                })
                .collect(Collectors.joining("\n"));

        if (!mergedStyles.isEmpty()) {
            DataNode styleData = new DataNode(mergedStyles);
            head.appendElement("style").appendChild(styleData);
        }

        // Collect unique links and append them to <head>
        Map<String, Element> uniqueLinks = new HashMap<>();
        customElements.collectedLinks().forEach(link -> {
            List<Attribute> linkAttrs = link.attributes().asList();
            String normalisedAttrs = linkAttrs.stream().sorted(Map.Entry.comparingByKey())
                    .map(attribute -> attribute.getKey() + "=" + attribute.getValue())
                    .collect(Collectors.joining(";"));
            uniqueLinks.put(normalisedAttrs, link);
        });
        uniqueLinks.values().forEach(head::appendChild);

        return bodyContent ? doc.body().html() : doc.html();
    }

    private ExpandedTemplate expandTemplate(Element element, Template template) {
        Attributes attributes = element.attributes();
        Map<String, String> attrsMap = new HashMap<>();
        attributes.iterator().forEachRemaining(attr -> {
            attrsMap.put(attr.getKey(), attr.getValue());
        });
        String expandedString = template.render(attrsMap);
        Document fragment = Jsoup.parseBodyFragment(expandedString);
        return new ExpandedTemplate(fragment);
    }

    private static class ExpandedTemplate {
        Element fragment;
        CustomElements customElements;

        public ExpandedTemplate(Document document) {
            // Jsoup wraps parsed fragments in an entire Document
            this.fragment = document.body();
            this.customElements = new CustomElements();
            fragment.children().forEach((element) -> {
                if ("script".equals(element.tagName())) {
                    fragment.children().remove(element);
                    customElements.collectedScripts().add(element);
                }
                if ("style".equals(element.tagName())) {
                    fragment.children().remove(element);
                    customElements.collectedStyles().add(element);
                }
                if ("link".equals(element.tagName())) {
                    fragment.children().remove(element);
                    customElements.collectedLinks().add(element);
                }
            });
        }

        public Element fragment() {
            return fragment;
        }

        public List<Element> collectedStyles() {
            return customElements.collectedStyles();
        }

        public List<Element> collectedScripts() {
            return customElements.collectedScripts();
        }

        public List<Element> collectedLinks() {
            return customElements.collectedLinks();
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExpandedTemplate that = (ExpandedTemplate) o;
            return Objects.equals(fragment, that.fragment) && Objects.equals(customElements, that.customElements);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fragment, customElements);
        }

        @Override
        public String toString() {
            return "ExpandedTemplate[" +
                    "fragment=" + fragment +
                    ", customElements=" + customElements +
                    ']';
        }
    }

    private static class CustomElements {
        private final List<Element> collectedStyles = new ArrayList<>();
        private final List<Element> collectedScripts = new ArrayList<>();
        private final List<Element> collectedLinks = new ArrayList<>();

        public CustomElements() {
        }

        public List<Element> collectedStyles() {
            return collectedStyles;
        }

        public List<Element> collectedScripts() {
            return collectedScripts;
        }

        public List<Element> collectedLinks() {
            return collectedLinks;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CustomElements that = (CustomElements) o;
            return Objects.equals(collectedStyles, that.collectedStyles) && Objects.equals(collectedScripts, that.collectedScripts) && Objects.equals(collectedLinks, that.collectedLinks);
        }

        @Override
        public int hashCode() {
            return Objects.hash(collectedStyles, collectedScripts, collectedLinks);
        }

        @Override
        public String toString() {
            return "CustomElements[" +
                    "collectedStyles=" + collectedStyles +
                    ", collectedScripts=" + collectedScripts +
                    ", collectedLinks=" + collectedLinks +
                    ']';
        }
    }
}
