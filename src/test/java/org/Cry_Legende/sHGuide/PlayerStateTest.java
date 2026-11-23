package org.Cry_Legende.sHGuide;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStateTest {

    @Test
    void oldJsonWithoutCollectionsIsNormalized() {
        PlayerState s = new Gson().fromJson("{\"name\":\"Bot\",\"streak\":3}", PlayerState.class).normalize();
        assertEquals(3, s.streak);
        assertTrue(s.pending.isEmpty());
        assertTrue(s.baselines.isEmpty());
        assertTrue(s.done.isEmpty());
        assertTrue(s.jokerWeeks.isEmpty());
    }
}
