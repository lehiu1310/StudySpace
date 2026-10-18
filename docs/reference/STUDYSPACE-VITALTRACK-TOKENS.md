# StudySpace / VitalTrack Reference Tokens

Extracted from `vitaltrack-source/src/index.css`, `index.html`, and the Tailwind utility classes in `src/pages/public/LandingPage.jsx`. The values below were recorded before the StudySpace landing page was styled.

## Colors

| Role | Exact value |
|---|---|
| Primary ramp 50 / 100 / 200 / 300 / 400 | `#eef2ff` / `#e0e7ff` / `#c7d2fe` / `#a5b4fc` / `#818cf8` |
| Primary ramp 500 / 600 / 700 / 800 / 900 | `#6366f1` / `#4f46e5` / `#4338ca` / `#3730a3` / `#312e81` |
| Primary foreground | `#ffffff` |
| Accent ramp 50 / 100 / 200 / 500 / 600 / 700 | `#f0fdfa` / `#ccfbf1` / `#99f6e4` / `#14b8a6` / `#0d9488` / `#0f766e` |
| Background token / root background | `#f8fafc` / `#fafbff` |
| Foreground token / root text | `#0f172a` / `#1e293b` |
| Surface / hover / muted | `#ffffff` / `#f1f5f9` / `#f1f5f9` |
| Muted foreground | `#64748b` |
| Border / border hover | `#e2e8f0` / `#cbd5e1` |
| Success / warning / error / info | `#10b981` / `#f59e0b` / `#ef4444` / `#0ea5e9` |
| Landing gradient | `linear-gradient(135deg, #4f46e5 0%, #14b8a6 100%)` |

## Typography

| Use | Exact source value |
|---|---|
| Body stack | `'Inter', system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif` |
| Display stack | `'Outfit', 'Inter', system-ui, -apple-system, sans-serif` |
| Web font weights | Inter and Outfit, `300 400 500 600 700 800`, Google Fonts stylesheet in `index.html` |
| Landing heading classes | `text-5xl` = 48px, `sm:text-6xl` = 60px, `xl:text-7xl` = 72px |
| Landing supporting copy | `text-lg` = 18px, `sm:text-xl` = 20px |
| Common utility sizes present | `text-[10px]`, `text-[11px]`, `text-xs` = 12px, `text-sm` = 14px, `text-base` = 16px, `text-2xl` = 24px, `text-3xl` = 30px |

Tailwind v4 is used without an overriding spacing scale. Its default spacing unit is `0.25rem` (4px); for example, `gap-4` = 16px, `gap-6` = 24px, `px-8` = 32px, and `pt-32` = 128px. The landing also uses `max-w-7xl` (1280px).

## Radius and Elevation

| Token | Exact value |
|---|---|
| `--radius-sm` / `md` / `lg` / `xl` / `2xl` | 8px / 12px / 16px / 24px / 32px |
| `--shadow-xs` | `0 1px 2px rgba(15, 23, 42, 0.05)` |
| `--shadow-sm` | `0 4px 12px -2px rgba(15, 23, 42, 0.04), 0 2px 4px -2px rgba(15, 23, 42, 0.02)` |
| `--shadow-md` | `0 12px 28px -6px rgba(15, 23, 42, 0.08), 0 8px 12px -4px rgba(15, 23, 42, 0.04)` |
| `--shadow-lg` | `0 24px 48px -12px rgba(15, 23, 42, 0.12), 0 12px 24px -8px rgba(15, 23, 42, 0.06)` |
| `--shadow-float` | `0 20px 40px -8px rgba(99, 102, 241, 0.15), 0 10px 20px -4px rgba(99, 102, 241, 0.1)` |
| `--shadow-glow` | `0 0 20px 0 rgba(99, 102, 241, 0.3)` |

Pill controls in the reference use Tailwind `rounded-full`; StudySpace expresses that utility as `999px` while retaining the exact named radius tokens above.

## Captures

- VitalTrack screens: `vitaltrack/desktop-1440x900/` and `vitaltrack/mobile-390x844/`.
- StudySpace current web baseline: `studyspace/desktop-1440x900/` and `studyspace/mobile-390x844/`.
- A1 comparison: `landing-comparison-desktop-1440x900.png` and `landing-comparison-mobile-390x844.png` (VitalTrack left, StudySpace right).
- All dashboard reference captures use in-memory mock API responses only; no account or database records were changed.
