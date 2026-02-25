package com.example.searchengine.util;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Cleans Wikipedia wiki-markup from raw article text.
 * Uses compiled patterns for performance. Does NOT use DOM parsing — pure regex.
 */
@Component
public class WikiMarkupCleaner {

    // Remove [[File:...]] and [[Image:...]] blocks
    private static final Pattern FILE_PATTERN =
            Pattern.compile("\\[\\[(File|Image|Datei):[^]]*]]", Pattern.CASE_INSENSITIVE);

    // [[link|display text]] -> display text; [[link]] -> link
    private static final Pattern WIKI_LINK_DISPLAY =
            Pattern.compile("\\[\\[[^|\\]]+\\|([^]]+)]]");

    private static final Pattern WIKI_LINK_PLAIN =
            Pattern.compile("\\[\\[([^]]+)]]");

    // Remove external links [http://... text] and bare http://... URLs
    private static final Pattern EXTERNAL_LINK =
            Pattern.compile("\\[https?://[^\\s\\]]+[^]]*]");

    private static final Pattern BARE_URL =
            Pattern.compile("https?://\\S+");

    // Remove {{template}} blocks (non-greedy, handles some nesting via iteration)
    private static final Pattern TEMPLATE =
            Pattern.compile("\\{\\{[^{}]*}}");

    // Remove HTML tags
    private static final Pattern HTML_TAGS =
            Pattern.compile("<[^>]+>");

    // Remove HTML entities
    private static final Pattern HTML_ENTITIES =
            Pattern.compile("&[a-zA-Z]{2,6};|&#\\d{1,6};");

    // Remove wiki headings markers (== Heading ==)
    private static final Pattern HEADINGS =
            Pattern.compile("={2,}([^=]+)={2,}");

    // Remove reference markers
    private static final Pattern REF_TAGS =
            Pattern.compile("<ref[^>]*/?>.*?</ref>|<ref[^/]*/?>", Pattern.DOTALL);

    // Remove tables (simplistic — removes {| ... |} blocks)
    private static final Pattern TABLE =
            Pattern.compile("\\{\\|.*?\\|}", Pattern.DOTALL);

    // Collapse multiple whitespace
    private static final Pattern MULTI_WHITESPACE =
            Pattern.compile("\\s{2,}");

    /**
     * Cleans the given raw wiki text and returns plain readable text.
     * Applies cleaners in a deliberate order to avoid partial-match artifacts.
     *
     * @param rawText raw wiki markup text
     * @return cleaned plain text
     */
    public String clean(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String text = rawText;

        // Order matters: remove complex structures before simple patterns
        text = REF_TAGS.matcher(text).replaceAll(" ");
        text = TABLE.matcher(text).replaceAll(" ");
        text = FILE_PATTERN.matcher(text).replaceAll(" ");

        // Iteratively remove nested templates (up to 5 levels deep)
        for (int i = 0; i < 5; i++) {
            String after = TEMPLATE.matcher(text).replaceAll(" ");
            if (after.equals(text)) break;
            text = after;
        }

        text = WIKI_LINK_DISPLAY.matcher(text).replaceAll("$1");
        text = WIKI_LINK_PLAIN.matcher(text).replaceAll("$1");
        text = EXTERNAL_LINK.matcher(text).replaceAll(" ");
        text = BARE_URL.matcher(text).replaceAll(" ");
        text = HEADINGS.matcher(text).replaceAll(" $1 ");
        text = HTML_TAGS.matcher(text).replaceAll(" ");
        text = HTML_ENTITIES.matcher(text).replaceAll(" ");

        // Remove remaining wiki markup characters
        text = text.replace("'''", "").replace("''", "").replace("*", "")
                   .replace("#", "").replace("|", " ").replace("!", "");

        text = MULTI_WHITESPACE.matcher(text).replaceAll(" ").strip();

        return text;
    }
}
