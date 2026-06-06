# Aether Agent Notes

## UI Design

- Aether's UI language avoids visible borders on controls, buttons, cards, and settings surfaces.
- Prefer soft fills, spacing, typography, and state color changes over stroked outlines.
- Do not introduce bordered Material controls unless the user explicitly asks for a border.

## Android Development

- Keep signing keys and release credentials outside the repository.
- Install an updated APK to the connected ADB device after code changes before handing off, unless the user explicitly asks otherwise.
- You can use ADB to interactively test the app on a connected device. Do not take screenshots since vision models are expensive. Instead, always drive your step-by-step actions (like clicks and swipes) solely by analyzing the real-time UI hierarchy after each move.
- UI hierarchy dumps are not validation by themselves. After installing a changed APK, use the hierarchy to find real nodes, compute tap/input coordinates, perform the actual workflow affected by the change, and re-dump the hierarchy or inspect device state to prove the feature behaved correctly. Merely launching the app, checking the focused activity, or confirming that a hierarchy dump succeeds is not sufficient.
- For visual UI defects that cannot be validated from hierarchy alone, such as cursor/text alignment, spacing, clipping, or rendering glitches, take a small number of targeted screenshots and inspect them. Screenshots are allowed for this kind of visual QA, but use them deliberately and avoid broad screenshot-heavy testing.

## Notes

- rg is not available on this machine
- Always exclude build and temporary folders when searching for files.
