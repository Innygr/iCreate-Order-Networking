package com.innygr.icreateordernetworking.mythic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MythicMessageSanitizationTest {
    private static final String ZWSP = Character.toString(0x200B);

    @Test
    void botApiNeutralizesDiscordMentionsAndNormalizesCarriageReturns() {
        String result = BotApiBridge.safe("@everyone\ralert @here", 100);

        assertEquals("@" + ZWSP + "everyone alert @" + ZWSP + "here", result);
        assertFalse(result.contains("@everyone"));
        assertFalse(result.contains("@here"));
    }

    @Test
    void webhookNeutralizesDiscordMentionsAndNormalizesCarriageReturns() {
        String result = DiscordBridge.sanitize("@everyone\ralert @here", 100);

        assertEquals("@" + ZWSP + "everyone alert @" + ZWSP + "here", result);
        assertFalse(result.contains("@everyone"));
        assertFalse(result.contains("@here"));
    }

    @Test
    void sanitizersKeepTheirLengthLimits() {
        assertTrue(BotApiBridge.safe("abcdefgh", 4).length() <= 4);
        assertTrue(DiscordBridge.sanitize("abcdefgh", 4).length() <= 4);
    }
}
