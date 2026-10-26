# Distilled Report Template Notes

- Reference: `C:\Users\ADMIN\AppData\Local\Temp\StudySpace_Report_Sample.docx`, exported from the supplied Google Docs link. The source remains unchanged.
- SHA-256: `A9F61985AC4E66AF54FE0F871514E3125AE600A15A35E2D3EAB78FCF21F51EB6`.
- Source: 92 rendered pages in a Word-exported PDF; 1 section; letter portrait, 8.50 x 11.00 in; 1.00 in margins on all sides; Times New Roman body; four inline images; 61 tables.
- Cover: centered school/faculty identity, university seal, report title, project title, advisor, member names/IDs, Hanoi date.
- Opening sequence: cover; member duties table; acknowledgements; contents; list of tables; list of figures; introduction; numbered chapters; appendices/references.
- Body conventions: formal Vietnamese academic prose; justified paragraphs; centered bold uppercase chapter headings; numbered subsections; black text; tables with thin black grid; figure labels beneath or adjacent to figures.
- Adaptation for StudySpace: retain the academic sequence and typography; replace every TOPIK-specific claim/content/image with verified StudySpace material. Use the StudySpace ERD, architecture and actual Playwright screenshots. Do not reuse sample students or TOPIK images. Do not claim Android visual proof where none exists.
- New report title: `Xây dựng nền tảng học tập trực tuyến StudySpace` with subtitle describing course management, quizzes, progress analytics and AI assistance.
- Authorized StudySpace authors: Nguyễn Hữu Lê Hiếu (23010895) and Phạm Văn Huấn (23010331). Advisor identity follows the supplied template because the user stated it is the same advisor: ThS. Vũ Thị Ngọc Anh.
- Known verification baseline (2026-10-22): PostgreSQL 18 tables / 102 columns / 26 FKs; Android 56/56 tests; backend 50/50; web E2E 24/24; debug APK built, no current Android device visual check; web screenshots are Playwright evidence. Gemini direct was verified on 2026-10-22 per `docs/DEMO_SCRIPT.md`, but key availability is environment-dependent.
- Fidelity gates: ensure no TOPIK/sample student names remain; no secrets/passwords in report; all figures have captions and accurate evidence context; report length at least 50 rendered pages; render PDF via installed Microsoft Word when bundled LibreOffice is unavailable, then rasterize PDF with bundled Poppler and inspect contact sheets plus representative pages.
