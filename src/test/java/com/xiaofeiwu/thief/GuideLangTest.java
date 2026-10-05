package com.xiaofeiwu.thief;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The guide screen asks the language files for the name and the text of each entry of each tab: every one has to be there, in both languages. */
class GuideLangTest {

    private static Set<String> keys(String language) throws IOException {
        String json = Files.readString(Path.of("src/main/resources/assets/thief/lang/" + language + ".json"), StandardCharsets.UTF_8);
        Set<String> keys = new HashSet<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        while (m.find()) {
            if (!m.group(2).isBlank()) {
                keys.add(m.group(1));
            }
        }
        return keys;
    }

    @Test
    void everyEntryHasANameAndATextInBothLanguages() throws IOException {
        for (String language : new String[]{"zh_cn", "en_us"}) {
            Set<String> keys = keys(language);
            int[] counts = {10, 8, 13};
            String[] tabs = {"people", "tools", "play"};
            for (int t = 0; t < tabs.length; t++) {
                for (int i = 1; i <= counts[t]; i++) {
                    assertTrue(keys.contains("thief.guide." + tabs[t] + "." + i + ".n"), language + " lacks the name of guide." + tabs[t] + "." + i);
                    assertTrue(keys.contains("thief.guide." + tabs[t] + "." + i + ".t"), language + " lacks the text of guide." + tabs[t] + "." + i);
                }
                assertTrue(keys.contains("thief.guide.tab." + tabs[t]), language + " lacks the tab " + tabs[t]);
            }
            assertTrue(keys.contains("thief.guide.title") && keys.contains("item.thief.thief_guide"), language + " lacks the title");
        }
    }

    @Test
    void noTextIsLongerThanWhatCanBeReadOnAPage() throws IOException {
        // a text of 600 characters is a long one for a page of this size; it is scrolled, but a limit keeps them readable
        String json = Files.readString(Path.of("src/main/resources/assets/thief/lang/zh_cn.json"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"(thief\\.guide\\.[a-z]+\\.\\d+\\.t)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        while (m.find()) {
            assertTrue(m.group(2).length() < 700, m.group(1) + " is " + m.group(2).length() + " characters");
        }
    }
}
