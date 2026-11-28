# Prism's icons

Photon's original Android module retains the upstream internal names `PrismIcons`
and `PrismIconData`; these are real Kotlin APIs, not the application ID.
Every SVG is named after its Kotlin icon (`Home.svg` becomes `PrismIcons.Home`).
The generator is **`old-photon/build.gradle.kts`**, task `generatePrismIcons`:

```sh
./gradlew :old-photon:generatePrismIcons
```

The separate supplied Firebase `app/` module does not use this generator.

## Drawing rules

- Use a 24 by 24 viewBox, round caps and round joins (the root's `stroke-width`). An element may set its own
  `stroke-width` (only `SearchBold` does).
- Shapes are circles, straight lines and arcs. Corners use one radius: 2 for large shapes, 1 to 1.5 for small ones.
- Play, pause, skip and the dots of a menu are filled; everything else is a line.

## What the build reads

Only `<path d>`, `<circle cx cy r>` and `<rect x y width height rx>`, plus:

| Where | Attribute | Meaning |
| --- | --- | --- |
| element | `class="f"` | Filled, no line. |
| element | `class="o"` | A line that the icon's selected twin fills. |
| root | `data-filled="tint"` | The icon has a selected twin (`NameFilled`) whose `o` shapes get a soft tint (tabs, toggles). |
| root | `data-filled="solid"` | The same, filled solid (a liked heart, a saved bookmark). |
| root | `data-mirror="true"` | Flipped in right-to-left languages (arrows, lists, volume). Play and skip never are. |

Anything else (another element, a transform, a gradient) fails the build with the file's name, rather than being
quietly dropped. The `<style>` line in each file only makes `class="f"` show filled in a browser.

## Looking at them

`IconRenderTest` draws every icon at 20, 24 and 32 dp in each weight (Thin, Regular, Bold), light and dark, onto
`old-photon/build/icon-previews/`.
