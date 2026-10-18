package com.studyspace.server;

final class AdaptiveQuizPolicy {
    private AdaptiveQuizPolicy() {}

    static int targetDifficulty(Double recentMastery) {
        if (recentMastery == null) return 2;
        if (recentMastery < 60) return 1;
        if (recentMastery < 80) return 2;
        return 3;
    }

    static double priority(int targetDifficulty, int difficulty, long attempts, long incorrect) {
        double errorRate = attempts == 0 ? 0 : Math.max(0, Math.min(1, incorrect / (double) attempts));
        return Math.abs(targetDifficulty - difficulty) * 100.0
            - errorRate * 30.0
            - (attempts == 0 ? 10.0 : 0.0);
    }
}
