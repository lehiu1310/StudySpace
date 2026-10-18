# AI provider configuration and verification

The backend reads `GEMINI_API_KEY` into the Spring property
`studyspace.gemini-api-key` in `src/main/resources/application.properties`.
The Gemini controller injects that property in `GeminiController.java`.
The key remains server-side; it is not returned by `/api/ai/health`.

AI output identifies its source as `gemini` (shown as “AI trực tiếp · Gemini”)
or `offline` (shown as “Gợi ý offline”). Teacher draft warnings are displayed
when the source material is incomplete. Student recommendations use saved quiz
results and show the offline provenance badge.

## Automated evidence

- `GeminiIntegrationTest.aiHealthIsAdminOnlyAndDoesNotExposeKey` verifies the
  no-key state and role protection.
- `GeminiProviderIntegrationTest.providerKeyUsesHeaderAndUntrustedInputIsSeparatedFromSystemInstruction`
  uses a local HTTP stub to verify a configured provider response and server-side
  key header; it does not call Google.
- `GeminiProviderIntegrationTest.invalidKeyAndQuotaResponsesUseOfflineChatFallback`
  simulates invalid-key and quota responses and verifies the offline fallback.
- `GeminiIntegrationTest` covers offline chat, explanations, review quiz, and
  teacher content drafts.

No real Gemini request was made against the local application in this run.
After the owner supplies/configures a valid key and confirms it is ready, run
the four live UI flows (lesson chat, wrong-answer explanation, recommendation,
and review quiz) and capture their screenshots. Automated provider-stub tests
are not evidence of a live Google API key or quota.
