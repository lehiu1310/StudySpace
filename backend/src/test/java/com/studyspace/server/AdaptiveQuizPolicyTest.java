package com.studyspace.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdaptiveQuizPolicyTest {
    @Test void choosesDifficultyFromRecentMastery() {
        assertEquals(2, AdaptiveQuizPolicy.targetDifficulty(null));
        assertEquals(1, AdaptiveQuizPolicy.targetDifficulty(59.9));
        assertEquals(2, AdaptiveQuizPolicy.targetDifficulty(60.0));
        assertEquals(2, AdaptiveQuizPolicy.targetDifficulty(79.9));
        assertEquals(3, AdaptiveQuizPolicy.targetDifficulty(80.0));
    }

    @Test void prioritizesTargetLevelThenMistakesAndUnseenQuestions() {
        double target = AdaptiveQuizPolicy.priority(1, 1, 2, 2);
        assertTrue(target < AdaptiveQuizPolicy.priority(1, 2, 0, 0));
        assertTrue(AdaptiveQuizPolicy.priority(1, 1, 2, 2) < AdaptiveQuizPolicy.priority(1, 1, 2, 0));
        assertTrue(AdaptiveQuizPolicy.priority(1, 1, 0, 0) < AdaptiveQuizPolicy.priority(1, 1, 2, 0));
    }
}
