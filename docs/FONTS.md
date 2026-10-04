# Bundled fonts — what is missing and how to add it

`FontCatalog` lists 33 faces. **Zero `.ttf` files are in the repository**, so every entry
currently renders in a substitute system family. The Widget Studio marks these with `*` and
shows `Fonts (0 of 33 installed)` rather than pretending otherwise.

This is not a bug to fix in code. It is missing binary assets, and binaries have licences
that must be checked one at a time before they go in a Play Store build.

## How to install one

1. Put the file in `app/src/main/res/font/`.
2. Name it exactly the `resName` in `FontCatalog.fonts` — e.g. `orbitron.ttf` for
   `FontSpec("orbitron", "Orbitron", "orbitron", …)`.
3. Lowercase, digits and underscores only. `Orbitron-Regular.ttf` **will not compile** —
   Android resource names reject capitals and hyphens.

Nothing else changes. `FontCatalog.typeface()` resolves it via `getIdentifier` at runtime and
`isInstalled()` starts reporting `true`.

## Licence status

Verify each licence yourself before shipping. This table is a starting point, not legal advice.

| Family | Entries | Likely source | Licence to confirm |
|---|---|---|---|
| DSEG7 / DSEG14 | 5 | keshikan.net/fonts-e.html | SIL OFL 1.1 — safe to bundle |
| Orbitron, Rajdhani, Audiowide, Exo 2, Michroma | 5 | Google Fonts | SIL OFL 1.1 — safe to bundle |
| Share Tech Mono, Space Mono, Roboto Mono | 3 | Google Fonts | SIL OFL 1.1 / Apache 2.0 — safe |
| JetBrains Mono | 1 | jetbrains.com/lp/mono | SIL OFL 1.1 — safe |
| IBM Plex Mono | 1 | github.com/IBM/plex | SIL OFL 1.1 — safe |
| Inter Tight, Montserrat, Poppins, Bebas Neue, Oswald, Lexend Deca | 6 | Google Fonts | SIL OFL 1.1 — safe |
| VCR OSD Mono | 1 | various freeware mirrors | **unclear — verify or drop** |
| Digital-7 | 2 | Style-7, "free for personal use" | **not redistributable — drop or license** |
| LED Counter, LED Dot Matrix, Dot Matrix, 5x7 Pixel | 4 | various | **unclear — verify or drop** |
| Flip Clock, Nixie Tube, Neon Glow, Neon Tubes, Cyberpunk | 5 | various | **unclear — verify or drop** |

Roughly **21 of 33 are straightforwardly OFL**. The other 12 are placeholders chosen for the
style they describe, not for a specific licensed file; expect to substitute an OFL equivalent.

## APK size

Each `.ttf` is 15–60 KB, and only Latin digits and punctuation are ever drawn by the clock
styles. Subsetting with `pyftsubset` (fonttools) to `0-9 : . / A-Z a-z` typically cuts a face
to 4–8 KB. All 33 unsubsetted would add roughly 1–1.5 MB; subset, closer to 200 KB.

```
pyftsubset Orbitron-Regular.ttf --unicodes="U+0020-007E" \
  --output-file=app/src/main/res/font/orbitron.ttf
```

Note that subsetting to Latin only is correct here — `FontCatalog` is used exclusively by the
clock renderers, which draw digits. UI text uses the system font and keeps full Arabic coverage.
