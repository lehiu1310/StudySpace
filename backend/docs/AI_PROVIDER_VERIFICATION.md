# AI provider configuration and verification

Updated: 2026-10-22.

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

## Live verification (2026-10-22)

- `git check-ignore .env.local` confirms the local configuration file is
  excluded from Git. The backend startup log reports only
  `Gemini configuration source: file .env.local`; it does not print the key.
- The latest authenticated `GET /api/ai/health` after restarting the server
  returned HTTP 200 with `configured=true` and `status=available` on
  2026-10-22. Health is a point-in-time observation and quota may change.
- A fixed student logged in and asked a question inside an actual unlocked
  lesson. Playwright verified that the request carried all 235/235 characters
  of that lesson's content, response provider was `gemini`, answer was
  non-empty and did not report missing context, and there were zero browser
  errors. Screenshot: `../test-evidence/demo-ai-student-chat-live-context-desktop.png`.
- The fixed student also requested an explanation for a wrong answer retrieved
  read-only from an existing submitted quiz. The endpoint returned a 357-character
  explanation with `provider=gemini`; no attempt or progress record was changed.
- The live review-quiz UI test checked the `AI trực tiếp · Gemini` badge, five
  generated questions, absence of answer keys/explanations before submit,
  controlled 4/5 server score after selecting one known-wrong option, and a
  direct Gemini explanation shown only for that wrong answer. It passed at
  390px with zero browser errors. The temporary submitted attempt and five
  inactive question rows were removed in cleanup. Screenshots:
  `../test-evidence/demo-ai-review-quiz-live-desktop.png` and
  `../test-evidence/demo-ai-review-quiz-live-mobile.png`;
  post-submit explanation: `../test-evidence/demo-ai-review-quiz-live-result-mobile.png`.
- The fixed teacher's live UI generated five quiz questions from a real chapter;
  Playwright verified five distinct questions, 20 choices, five explanations,
  the exact `AI trực tiếp · Gemini` label, no uncaught browser errors, and no
  horizontal overflow at 390px. Screenshots:
  `../test-evidence/demo-ai-teacher-quiz-live-desktop.png` and
  `../test-evidence/demo-ai-teacher-quiz-live-mobile.png`.
- The fixed teacher's course-draft endpoint returned a non-empty Gemini
  description and exactly four chapters for a concrete networking course topic;
  this was an unsaved draft and created no course row.
- The fixed teacher account generated a five-question draft from its real
  chapter after a live HTTP 502 duplicate-question failure was reproduced and
  fixed. Retest returned HTTP 200, `provider=offline`, five unique prompts,
  four choices and an explanation per item. The Playwright UI test also confirmed
  the drafts remain unsaved until teacher review; desktop/mobile evidence is
  `test-evidence/demo-ai-teacher-quiz-fixed-{desktop,mobile}.png`. Mobile at
  390px has no horizontal overflow.
- Previous live requests intermittently encountered timeouts/HTTP 503 and
  project quota limits. Retest the desired feature immediately before the demo
  and trust the visible provider label; an earlier `available` result does not
  guarantee later availability.
- The student and teacher web clients now allow 35 seconds for `/api/ai/*`
  requests, above the backend's 25-second upstream timeout. Ordinary API calls
  retain their 12-second timeout. A live teacher-generation request exceeded
  the old 12-second client limit before this fix; desktop and mobile retests
  passed afterward.
- Any previously exposed key must be revoked in AI Studio. Keep its replacement
  only in ignored `.env.local`; never paste it into source control, screenshots,
  or chat. After restart, verify health first.
- The packaged default model is now `gemini-3.5-flash-lite`
  (`application.properties`); this was live-verified after
  `gemini-3.8-flash` hit its free-tier daily limit. `GEMINI_MODEL` can still
  override the default. Google's [rate-limit guide](https://ai.google.dev/gemini-api/docs/rate-limits)
  states limits are project-level and vary by model. Rotating another key in
  the same project does not raise that project's quota; check its active tier,
  model quota and billing in AI Studio.
