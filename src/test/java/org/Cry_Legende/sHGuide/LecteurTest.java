package org.Cry_Legende.sHGuide;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LecteurTest {

    private long now;
    private final List<String> read = new ArrayList<>();
    private final Resolver source = text -> {
        read.add(text);
        return text + read.size();
    };

    @Test
    void aSlowPlaceholderIsKeptForItsSeconds() {
        Lecteur l = new Lecteur(() -> now);
        Resolver r = l.with(source, Map.of("%lent%", 30), false);
        assertEquals("%lent%1", r.resolve("%lent%"));
        now = 29_999;
        assertEquals("%lent%1", r.resolve("%lent%"));
        assertEquals("%vite%2", r.resolve("%vite%"));
        assertEquals("%vite%3", r.resolve("%vite%"));
        now = 30_000;
        assertEquals("%lent%4", r.resolve("%lent%"));
    }

    @Test
    void openingAScreenReadsItNow() {
        Lecteur l = new Lecteur(() -> now);
        l.with(source, Map.of("%lent%", 30), false).resolve("%lent%");
        assertEquals("%lent%2", l.with(source, Map.of("%lent%", 30), true).resolve("%lent%"));
        assertEquals("%lent%2", l.with(source, Map.of("%lent%", 30), false).resolve("%lent%"));
    }

    @Test
    void withoutSlowPlaceholdersNothingIsKept() {
        Lecteur l = new Lecteur(() -> now);
        Resolver r = l.with(source, Map.of(), false);
        assertEquals("%lent%1", r.resolve("%lent%"));
        assertEquals("%lent%2", r.resolve("%lent%"));
    }
}
