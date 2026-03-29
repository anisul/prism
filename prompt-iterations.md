# Prompt Iteration Log

## Iteration 1 — Baseline
**Date:** initial run  
**Change:** first prompt, no tuning  
**Results:** partial run only (rate limit)

| Metric | Score |
|---|---|
| Avg Precision | 1.00 (7 cases) |
| Avg Recall | 0.96 (7 cases) |
| F1 | 0.98 (7 cases) |
| Perfect cases | 5/7 |

**Issues found:**
- `searchText` retains filler words: `"products"`, `"something"`, `"furniture"`
- `size:L` extracted for furniture queries (semantically wrong)

---

## Iteration 2 — SearchText + Size Scope Fix
**Date:** Block 7 prompt fix  
**Changes:**
- Added explicit filler word strip list to searchText rule
- Scoped `size` filter to clothing and footwear only
- Added 5 new examples covering the failing cases
- Updated golden dataset: removed `size:L` from furniture cases

**Results:** *update after full eval run*

| Metric | Score |
|---|---|
| Avg Precision | |
| Avg Recall | |
| F1 | |
| Perfect cases | |

**Issues found:** *update after eval*