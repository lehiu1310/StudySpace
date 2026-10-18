# Room Schema History

This history is based on the database declaration, migration code, and legacy migration fixtures present in this workspace.

| Version | Known schema change |
| --- | --- |
| 3 | Oldest schema represented by a migration fixture. Contains `users`, `study_tasks`, `focus_sessions`, and `reward_logs`; `study_tasks` has no `completedAt` column. |
| 4 | Adds `group_chat_messages` with `MIGRATION_3_4`. |
| 5 | Adds nullable `study_tasks.completedAt` with `MIGRATION_4_5`. This is the current schema. |

No Room schema export, migration, or fixture for versions 1 or 2 exists in this workspace, so their table layouts cannot be verified and are not guessed here. If databases from those versions were distributed, their original schema or an anonymized database file is required before a data-preserving migration can be written.

Destructive migration fallback is intentionally not configured. An unsupported database version must fail visibly rather than silently erase local study data. Room schema export is enabled and KSP is configured to write future schema snapshots under `app/schemas`.
