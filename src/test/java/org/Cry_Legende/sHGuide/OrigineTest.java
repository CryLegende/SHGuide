package org.Cry_Legende.sHGuide;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrigineTest {

    private static final Component JOBS = Component.text("Métiers");
    private static final Component JOB = Component.text("Mineur");

    @Test
    void onlyTheMenuOpenedByJakeLeadsBackToHim() {
        Origine.Depart d = new Origine.Depart("taches", 0, null, null);
        assertFalse(d.inFirst());
        d = d.opened(JOBS);
        assertTrue(d.inFirst());
        d = d.opened(JOB);
        assertFalse(d.inFirst());
        d = d.opened(Component.text("Métiers"));
        assertTrue(d.inFirst());
        assertEquals("taches", d.screen());
    }
}
