# pasted_content_id301

> 생성일시: 2026-11-06 10:11

## 질문

<pasted_content id="3019">
dstone-knowledge 를 제품화 하고 싶어.
그래서 솔루션소개서를 만들려고해.

dstone-boot > knowledge 메뉴로 들어가서 보여지는 화면과 기능을 상업적인 목적에 부합하게 매력적인 부분을 부각시켜서 기능 설명서를 만들어줘.
샘플템플릿을 참고해서 해당 문서에 준하게 만들어줘.

<샘플템플릿>
/app/dstone/docs/temp/01.AI에이전트-FOR-SM.pptx

<요구사항>
1. 약 20~30 페이지 분량.
2. 화면 캡쳐를 이용한 풍부한 비주얼.
3. 문서만 보고도 이해할 수 있을만한 상세하고 쉽고 직관적인 설명.

<아웃풋>
/app/dstone/docs/temp/02.KNOWLEGE(분석Tool).pptx
</pasted_content id="3019">

---

## 질문

Base directory for this skill: /home/jysn007/.claude/skills/synced/5eea7ea1-050e-4163-afc6-ba4688fcbe71_e8438320-09bc-417c-be34-e518da81941e/pptx

# PPTX creation, editing, and analysis

If this session offers a dedicated slide-deck artifact type or a separate slides skill, and the user has neither asked for a PowerPoint/.pptx file nor supplied a .pptx/.potx file to edit, fill in, or convert, build the deck with that type or skill instead. A .pptx/.potx file given only as source material or as an example for a new deck ("make another deck like this one") does not count as supplied: read it with this skill, then build the new deck with that type or skill. This skill remains the right tool for producing .pptx files and for reading, editing, templating, or converting existing .pptx/.potx files.

A `.pptx` is a ZIP archive of XML files. Choose your approach by task:

| Task | Approach |
|---|---|
| **Create** a new deck | Write a `pptxgenjs` script — see gotchas below — and build it as a [structured deck](#structured-decks) unless the user asks for a quick throwaway |
| **Edit** an existing deck, or build from a template | unzip → edit `ppt/slides/slideN.xml` → zip |
| **Read** content | `python scripts/slide_text.py deck.pptx` (one block per slide under `<!-- Slide number: N -->` markers); visual grid: `python scripts/thumbnail.py deck.pptx` |

## Scripts

Paths are relative to this skill's directory. Everything else is plain Python, `node`, or shell.

| Script | What it does |
|---|---|
| `scripts/slide_text.py deck.pptx` | Every slide's title, text, tables, chart data (scatter and bubble charts: series names only), picture alt text and speaker notes, as markdown. Reads `.potx` too |
| `scripts/thumbnail.py deck.pptx [prefix]` | Labeled grid of every slide, for picking template layouts. `.pptx` only. Pass `prefix` — it defaults to `thumbnails`, which overwrites the grids of any other deck done in the same directory |
| `scripts/add_slide.py unpacked/ slide2.xml [--after slideN.xml]` | Duplicate a slide (or a `slideLayoutN.xml`) with all the package bookkeeping. Also takes a `.pptx` directly with `-o out.pptx` |
| `scripts/clean.py unpacked/` | Delete slides, media, and rels no longer referenced. Run **after** `<p:sldIdLst>` is final |
| `scripts/apply_theme.js` | Node module: `const { applyTheme } = require("<this skill's directory>/scripts/apply_theme.js")`. `await applyTheme("deck.pptx", THEME)` after `pres.writeFile()` writes `THEME`'s colors and name (escaped for XML) into the deck's theme, for [structured decks](#structured-decks). Also a CLI: `node scripts/apply_theme.js deck.pptx theme.json` |
| `scripts/office/validate.py deck.pptx [--original src.pptx]` | Schema, relationship, content-type, chart and slide checks; each failure names its fix. Pass `--original` for any template-derived deck — it baselines the schema checks against the template, so the template's own XSD errors don't read as yours |
| `scripts/office/soffice.py --headless --convert-to pdf deck.pptx` | LibreOffice wrapper — bare `soffice` hangs in this sandbox |

## Creating with pptxgenjs — gotchas

`pptxgenjs` is preinstalled — do not run `npm install` first; write the script and `require('pptxgenjs')` directly. Only if that require fails: `npm install pptxgenjs`. The model knows the API; these are the footguns:

- **Set `pres.layout` before adding slides.** The default canvas is `LAYOUT_16x9` = **10" × 5.625"**, not 13.3" wide. Coordinates past the edge are written, not clamped — the shape just isn't on the slide. (`LAYOUT_WIDE` is 13.3" × 7.5".)
- **Hex colors: never `#`, never 8 digits.** `color: "FF0000"`. Both `"#FF0000"` and alpha baked into the hex (`"00000020"`) **corrupt the file**. For translucency: `transparency: 0-100` on fills and images, `opacity: 0.0-1.0` on shadows — each is silently ignored on the other.
- **pptxgenjs mutates option objects in place** (converts values to EMU on first use). Never share one `shadow`/options object across two `add*` calls — build a fresh object each time.
- **Shadow `offset` must be ≥ 0** — a negative offset corrupts the file. To cast a shadow upward, use `angle: 270` with a positive offset.
- **`letterSpacing` is silently ignored** — the real option is `charSpacing`.
- **Lists:** `bullet: true` on each item, never a literal `•` (renders double bullets). Set `breakLine: true` on every array item except the last. Space bulleted paragraphs with `paraSpaceAfter`, not `lineSpacing` (huge gaps).
- **One `new pptxgen()` per output file** — never reuse an instance.
- **`rectRadius` only works on `ROUNDED_RECTANGLE`**, not `RECTANGLE`.
- **Gradient fills aren't supported** — use a gradient image as the background instead.
- **Every `addText` call without a `placeholder` needs `isTextBox: true`** — without it the shape lacks `txBox="1"`, so screen readers announce the text as a "graphic" instead of a text box. No visual change.
- **Text boxes have built-in internal padding** — set `margin: 0` whenever text must align with a shape, line, or icon at the same x.
- **Speaker notes go in `slide.addNotes("...")`** (plain text, once per slide), never in a text box on the slide.
- **Keep charts native.** Use `addChart()` for everything PowerPoint can chart (pass an array of `{type, data, options}` for combos). For PowerPoint-native features the library doesn't expose (trendlines, error bars), compute the extra series yourself or post-process the generated OOXML — do not fall back to a rendered image. Only chart types PowerPoint has no native form for (Sankey, network, chord) go in as images.
- **Default charts render bare** — no title, no data labels, dated palette. Set `showTitle` + `title`, `showValue: true` + `dataLabelPosition`, `chartColors: [...]` from your palette, and quiet the frame (`catAxisLabelColor`/`valAxisLabelColor`, `valGridLine: { color, size }`, `catGridLine: { style: "none" }`, `showLegend: false` for a single series).
- **On a stacked bar or column chart, `dataLabelPosition` must be `ctr`, `inEnd`, or `inBase`.** `outEnd` **corrupts the file**.
- **A combo series using `secondaryValAxis`/`secondaryCatAxis` needs both `valAxes` and `catAxes` on the chart options, two entries each.** Without them pptxgenjs writes axis *ids* it never declares, and PowerPoint **discards that chart** and reports the file as corrupt. Supplying only `valAxes` is not enough.
- **After `writeFile()`, run `python scripts/office/validate.py deck.pptx`.** It reports many of the chart and slide-XML faults PowerPoint refuses or repairs, the two above among them, and names the fix for each. Fix them in your generator, not by hand-editing the packed XML.
- **Never reorder the children of `<p:presentation>`.** pptxgenjs writes `<p:notesMasterIdLst>` right after `<p:sldIdLst>` and points both masters at one theme part. PowerPoint reads that happily — move the element and the same deck becomes unopenable.
- **Icons:** render `react-icons` to SVG (`ReactDOMServer.renderToStaticMarkup`), rasterize with `sharp` at ≥256px, and insert via `addImage({ data: "image/png;base64," + buf.toString("base64") })` — the `image/png;base64,` prefix is required (`react-icons`, `react`, `react-dom`, and `sharp` are preinstalled — `npm install react-icons react react-dom sharp` only if a require fails).
- **`defineSlideMaster()` creates a slide *layout*, not a master** — one `slideLayoutN.xml` per call, under a single master you cannot change. Call them layouts when talking to the user.
- **`pres.company` and a `defineSlideMaster()` `title` are written into the XML unescaped** — an `&` in either (`"Harrow & Sons"`) leaves `docProps/app.xml` or the layout unparseable, and `validate.py` fails. Write `&amp;` there, and in the matching `masterName`. `title`, `subject`, `author`, section titles, `objectName`, notes and slide text are escaped for you.

### Structured decks

A deck built on a theme, layouts, placeholders and sections can be restyled in one place and edited in PowerPoint without fighting it. Build every new deck this way unless the user asks for a quick throwaway.

1. **Theme first:** `const THEME = { name, headFontFace, bodyFontFace, colors: { dk1, lt1, dk2, lt2, accent1 … accent6, hlink, folHlink } }`, colors in hex. `pres.theme` takes the two fonts and nothing else. Name no font after that — titles get the heading font, other text the body font — except on chart text, which is hard-coded Arial: give its `*FontFace` options `"+mn-lt"` (theme body font; `"+mj-lt"` is the heading font).
2. **`const C = pres.SchemeColor` for every color** — `C.text1`/`C.text2` are `dk1`/`dk2`, `C.background1`/`C.background2` are `lt1`/`lt2`. These take hex only, read from `THEME`: `valGridLine.color`, `catGridLine.color`, `shadow.color`, and on a one-series bar chart `invertedColors` or more than one `chartColors`. A scheme color there is written as `<a:srgbClr val="bg2"/>` without complaint, and being hex, these will not follow a later change of theme.
3. **Sections:** `pres.addSection({ title })` just before that section's first slide, then `sectionTitle` on every `addSlide`. A slide without one — or, when sections are all declared up front, a table's auto-paged continuation slides — lands in a stray "Default-N" section.
4. **One layout per slide frame** (`defineSlideMaster()`): title, section divider, title only, chart + commentary, table; dark and light variants if the design alternates. Text regions are named placeholders carrying position, size, weight and `color` — chart, image and table placeholders need a `color` too, or prompt text and arrays of runs come out hard-coded `000000`. Footer text, logo and `slideNumber` go on the layout, never on a slide.
5. **Fill by name:** `addSlide({ masterName, sectionTitle })`, then `addText("…", { placeholder: "title" })` with no position, size or color — the placeholder's own options win, and anything it doesn't set (italic, `align`, `fontFace`) still applies; style one run with `[{ text, options }]`. A misspelt `placeholder` or `masterName` fails silently (a stray text box at 0,0; the blank layout). Only `title`, `body` and `chart` placeholders fill by name alone: `addImage({ placeholder })` takes the placeholder's x and y but not its size (a 1" × 1" picture) — pass `w`/`h` too. `addTable({ placeholder })` takes its x and y only — always pass `w`; when it auto-pages it ignores the placeholder and runs off the slide, so also pass `x`, `y` and `autoPageSlideStartY` (same as `y`, or continuation pages are laid out from the top margin), give the layout a `margin` whose bottom clears the footer, and fill the continuation slides' titles through `slide.newAutoPagedSlides`.
6. **A layout is the frame, not the "layout" Design Ideas means.** Cards, icons, stat callouts and images are still composed slide by slide on top of it, in scheme colors, each with an `objectName` — every Design Idea applies, and most content slides sit on the title-only layout.
7. **Write, then apply the theme:** `await pres.writeFile({ fileName: "deck.pptx" })`, then `await applyTheme("deck.pptx", THEME)`. pptxgenjs cannot write theme colors: until this runs, scheme colors resolve to Office's stock palette. It throws, changing nothing, on a scheme color in a hex-only option — fix what it names and rebuild.

## Editing existing decks and templates

Pick layouts first: `python scripts/thumbnail.py template.pptx template-thumbs` writes a labeled grid of every slide and prints the file(s) it created — `template-thumbs.jpg`, split into `template-thumbs-N.jpg` past 12 slides. **Always pass that second argument, named after the deck.** It defaults to `thumbnails`, so two decks thumbnailed in one directory silently overwrite each other's grids — the first deck's are simply gone (template analysis only — visual QA needs the full-resolution renders from [Converting to Images](#converting-to-images); it only accepts `.pptx`, so copy a `.potx` to a `.pptx` name first). Use it with `slide_text.py` to map each content section onto a template slide, and vary the layouts — don't put every section on the same title-and-bullets slide.

```bash
python3 -c "import sys,zipfile; zipfile.ZipFile(sys.argv[1]).extractall('unpacked')" deck.pptx
python scripts/add_slide.py unpacked/ slide2.xml --after slide2.xml   # duplicate a slide (or slideLayoutN.xml); prints the new slide's path
# reorder / delete slides = edit <p:sldIdLst> in ppt/presentation.xml
python scripts/clean.py unpacked/                                     # after deletions: removes orphaned slides, media, rels
# edit slide content in ppt/slides/slideN.xml
(cd unpacked && rm -f ../out.pptx && zip -XrD ../out.pptx .)          # zip from INSIDE the dir; rm first or deleted parts survive
python scripts/office/validate.py out.pptx --original deck.pptx
```

- **Do all structural work — add, delete, reorder — before editing any slide's content.** `add_slide.py` copies a slide file verbatim, so duplicating after you edit clones the edited content; and `clean.py` deletes any slide missing from `<p:sldIdLst>`, including one you just wrote.
- **Never copy a slide file by hand** — `add_slide.py` does every registration a new slide needs and reports what it made (`Created ppt/slides/slide17.xml from slide2.xml`). It also works directly on a file: `add_slide.py deck.pptx slide2.xml -o out.pptx` — **pass `-o`, or it rewrites the input deck in place.** A duplicated slide still *references* its source's chart/SmartArt/embedded-object parts rather than cloning them, so editing one slide's chart changes the other's.
- **If you use `python-pptx`**, three things it won't do: duplicate a slide (its only entry point is `add_slide(layout)`), preserve formatting through `text_frame.text = "..."` (that collapses the paragraph to a single unstyled run — assign `run.text` instead), or read the SVG/EMF most template art uses (`add_picture` raises `UnidentifiedImageError`).
- Legacy `.ppt` must be converted first: `python scripts/office/soffice.py --headless --convert-to pptx file.ppt`. `.potx` templates unpack and pack identically — keep the `.potx` extension on the output.
- To reuse a template icon or image, duplicate a slide or layout that already contains it.

When filling in a template:

- If you script an XML transform, parse with `defusedxml.minidom` — round-tripping OOXML through `xml.etree.ElementTree` rewrites namespace prefixes and corrupts the deck.
- **Template slots ≠ source items.** If the template shows 4 team members and you have 3, delete the 4th member's entire group (image + text boxes), not just its text — then check for orphaned visuals in QA.
- One `<a:p>` per list item — never concatenate items into a single paragraph. Copy the sibling `<a:pPr>` to preserve spacing, and put `b="1"` on the `<a:rPr>` of titles, section headers, and inline labels (`Status:`, `Owner:`).
- Let bullets inherit from the layout; only add `<a:buChar>`, `<a:buAutoNum>` (numbered), or `<a:buNone>` to override — never a literal `•` in the text.
- Text with leading or trailing spaces needs `xml:space="preserve"` on its `<a:t>`.

## Deck content and flow

- **One message per slide.** A deck goes theme by theme, message by message.
- **A common structure is the pyramid:** the main message up front, an executive summary that previews the sections, the sections themselves, then a conclusion, usually with next steps that act on the findings.
- **Match the structure to the size of the deck.** A short deck might not need an introduction and a conclusion; a long deck might need dividers between topics.
- **Build toward what the deck is for:** a decision, consensus, a celebration, resolving a disagreement, or opening a discussion.
- **Build for how it will be used.** A live deck gets narration and possibly discussion; a pre-read has to carry the whole narrative itself. An executive audience in a live meeting needs few words and highly visual slides. Detailed, word-heavy slides are sometimes right for subject-matter experts or for a deck read offline. Analytical reports lean on charts and tables.
- **Size a live deck to the talk:** about 1.5 to 2 minutes per slide, with time left for questions if there will be any.

## Design Ideas

**Don't create boring slides.** Plain bullets on a white background won't impress anyone. Consider ideas from this list for each slide.

### Before Starting

- **Pick a bold, content-informed color palette**: The palette should feel designed for THIS topic. If swapping your colors into a completely different presentation would still "work," you haven't made specific enough choices.
- **Dominance over equality**: One color should dominate (60-70% visual weight), with 1-2 supporting tones and one sharp accent. Never give all colors equal weight.
- **Dark/light contrast**: Dark backgrounds for title + conclusion slides, light for content ("sandwich" structure). Or commit to dark throughout for a premium feel.
- **Commit to a visual motif**: Pick ONE distinctive element and repeat it — rounded image frames, icons in colored circles. Carry it across every slide. **Do not use a color bar or accent stripe as your motif** (see Avoid list).

### Color Palettes

Choose colors that match your topic — don't default to generic blue. Use these palettes as inspiration:

| Theme | Primary | Secondary | Accent |
|-------|---------|-----------|--------|
| **Midnight Executive** | `1E2761` (navy) | `CADCFC` (ice blue) | `FFFFFF` (white) |
| **Forest & Moss** | `2C5F2D` (forest) | `97BC62` (moss) | `F5F5F5` (cream) |
| **Coral Energy** | `F96167` (coral) | `F9E795` (gold) | `2F3C7E` (navy) |
| **Warm Terracotta** | `B85042` (terracotta) | `E7E8D1` (sand) | `A7BEAE` (sage) |
| **Ocean Gradient** | `065A82` (deep blue) | `1C7293` (teal) | `21295C` (midnight) |
| **Charcoal Minimal** | `36454F` (charcoal) | `F2F2F2` (off-white) | `212121` (black) |
| **Teal Trust** | `028090` (teal) | `00A896` (seafoam) | `02C39A` (mint) |
| **Berry & Cream** | `6D2E46` (berry) | `A26769` (dusty rose) | `ECE2D0` (cream) |
| **Sage Calm** | `84B59F` (sage) | `69A297` (eucalyptus) | `50808E` (slate) |
| **Cherry Bold** | `990011` (cherry) | `FCF6F5` (off-white) | `2F3C7E` (navy) |

### For Each Slide

**Every slide needs a visual element** — image, chart, icon, or shape. Text-only slides are forgettable.

**Layout options:**
- Two-column (text left, illustration on right)
- Icon + text rows (icon in colored circle, bold header, description below)
- 2x2 or 2x3 grid (image on one side, grid of content blocks on other)
- Half-bleed image (full left or right side) with content overlay

**Data display:**
- Large stat callouts (big numbers 60-72pt with small labels below)
- Comparison columns (before/after, pros/cons, side-by-side options)
- Timeline or process flow (numbered steps, arrows)

**Visual polish:**
- Icons in small colored circles next to section headers
- Italic accent text for key stats or taglines

### Typography

**Font names you write into the .pptx are rendered by the user's PowerPoint, not by this environment.** Your visual QA renders via LibreOffice, which substitutes fonts it doesn't have — and for some fonts the substitute has different widths, so your QA preview can show text overflow (or fit) that the real deck won't have. To keep your QA trustworthy:

- **Safe fonts** (render true-to-width in QA *and* ship with Office): **Arial, Calibri, Cambria, Times New Roman, Courier New, Bookman Old Style, Century Schoolbook**. Use these for body text and anything where fit matters.
- **Headers with personality at zero QA risk**: pair a safe-list serif header (Cambria, Bookman Old Style, Century Schoolbook) with a safe-list sans body (Calibri or Arial). You get visual contrast without giving up reliable overflow checks.
- **If the user asks for a font outside the safe list** (e.g. Georgia or Trebuchet MS): use it where the user asked, but size those containers with extra slack (~10%) and don't trust QA text-fit on those elements — the preview of that font is approximate. If the user hasn't specified, prefer safe-list fonts for body text.
- **QA-unreliable fonts** (substitute has different widths — overflow checks can be wrong): Georgia, Trebuchet MS, Impact, Arial Black, Garamond, Consolas, Palatino Linotype. Calibri Light substitution varies by environment; treat as QA-unreliable. Fine for titles/accents with slack; don't trust QA text-fit on these.
- **Never default to Aptos** — Office's post-2023 default has no metric-compatible substitute here *and* is missing from older Office installs, so it's unreliable on both ends.

| Element | Size |
|---------|------|
| Slide title | 36-44pt bold |
| Section header | 20-24pt bold |
| Body text | 14-16pt |
| Captions | 10-12pt muted |

### Spacing

- 0.5" minimum margins
- 0.3-0.5" between content blocks
- Leave breathing room—don't fill every inch

### Avoid (Common Mistakes)

- **Don't repeat the same layout** — vary columns, cards, and callouts across slides
- **Don't end a slide title with a period**
- **Don't let titles drift between slides of the same type** — content slides with the title at the top put it at the same position, font, size, and alignment on every slide; a different slide type (a section divider with the title mid-slide) can place it differently
- **Don't change how chart labels look from chart to chart** — axis and data labels use the same color, font, and size on every chart in the deck; vary one only where a chart needs it (a second axis, a highlighted series, a label sitting on a dark bar)
- **Don't center body text** — left-align paragraphs and lists; center only titles
- **Don't skimp on size contrast** — titles need 36pt+ to stand out from 14-16pt body
- **Don't default to blue** — pick colors that reflect the specific topic
- **Don't mix spacing randomly** — choose 0.3" or 0.5" gaps and use consistently
- **Don't style one slide and leave the rest plain** — commit fully or keep it simple throughout
- **Don't create text-only slides** — add images, icons, charts, or visual elements; avoid plain title + bullets
- **Don't forget text box padding** — when aligning lines or shapes with text edges, set `margin: 0` on the text box or offset the shape to account for padding
- **Don't use low-contrast elements** — icons AND text need strong contrast against the background; avoid light text on light backgrounds or dark text on dark backgrounds
- **NEVER use accent lines under titles** — these are a hallmark of AI-generated slides; use whitespace or background color instead
- **NEVER add decorative color bars or accent stripes** — this includes: header/footer bars spanning the slide width, vertical sidebar stripes down one edge of the slide, thin accent stripes along one edge of a card or content block, and "single-side borders" on rectangles. These read as AI-generated filler. If you want to set a card apart, use a subtle background tint, a drop shadow, or an icon — not an edge stripe.
- **Don't default to cream/beige backgrounds** — when no background is specified, use white (`FFFFFF`) or the user's brand palette; avoid warm-neutral defaults like `F5F5DC`, `FAF0E6`, `FAEBD7`, `FFF8E1`
- **Don't ship text that overflows its shape** — if text doesn't fit, reduce font size, split across slides, or enlarge the container; never leave content cut off or spilling past bounds. **Don't shrink below the sizes in the Typography table** (body text 14pt, captions 10pt) in a deck you create — once you reach that floor, cut words, enlarge the container, or split the slide instead; when editing an existing deck or template, match its sizes

## QA (Required)

Your first render usually has a few real issues — overlaps, overflow, misalignment. Find and fix those, re-render only the slides you changed, and stop.

### Content QA (required)

```bash
python scripts/slide_text.py output.pptx
```

Check for missing content, typos, wrong order.

**When using templates, check for leftover placeholder text:**

```bash
python scripts/slide_text.py output.pptx | grep -iE "\bx{3,}\b|lorem|ipsum|\bTODO|\[insert|this.*(page|slide).*layout"
```

If grep returns results, fix them before declaring success.

### File QA (required)

```bash
python scripts/office/validate.py output.pptx                      # built from scratch
python scripts/office/validate.py output.pptx --original src.pptx  # built from a template
```

**If the deck came from a template, always pass `--original`.** A template may itself
contain parts the XSD rejects, so a bare run can report failures you never caused — and
a genuine regression can hide among them. `--original` baselines
the schema and slide checks against the template, suppressing errors it already had.
The structural checks — relationships, content types, charts — ignore `--original` and
report template-inherited problems either way, so read those on their own merits.

pptxgenjs emits chart XML PowerPoint refuses to open, and every other tool
accepts: python-pptx opens those decks, LibreOffice renders them, the XSD
passes them. Every failure names its fix. Fix it in the generator and rebuild.

**A pass is not proof of a valid file:** `validate.py` does not catch everything PowerPoint repairs (a chart element repeated past its limit passes); when you delete an animated shape from a template slide, delete the slide's `<p:timing>` element too (its animations go with it), or PowerPoint offers to repair the deck.

### Visual QA (required)

Convert the slides to images (see [Converting to Images](#converting-to-images)) and inspect every one. After staring at the generating code you tend to see what you expect rather than what rendered, so look at the images fresh (a subagent works well for this if you have one). User-visible defects to look for:

- **Text overflow or text cut off at a box or slide boundary — check this first.** It is the most common defect and always user-visible. (For a font the previewer renders unreliably per Typography, the preview is approximate: trust the ~10% slack you left, not its apparent fit.)
- Overlapping elements (text through shapes, lines through words, stacked elements)
- Source citations or footers colliding with content above
- Elements too close (< 0.3" gaps) or cards/sections nearly touching
- Uneven gaps (large empty area in one place, cramped in another)
- Insufficient margin from slide edges (< 0.5")
- Columns or similar elements not aligned consistently
- Low-contrast text (e.g., light gray text on cream-colored background)
- Template decoration mispositioned after text replacement — e.g., a title underline positioned for one line, but the replaced title wrapped to two
- Low-contrast icons (e.g., dark icons on dark backgrounds without a contrasting circle)
- Text boxes too narrow causing excessive wrapping
- Titles at different heights, or chart labels that look different from chart to chart, across slides of the same type — compare slides with each other, not only each slide with itself
- A word or number broken across two lines — a word split mid-word, or a figure separated from its unit ("26" on one line, "M" on the next)
- Leftover placeholder content

## Converting to Images

Convert presentations to individual slide images for visual inspection:

```bash
python scripts/office/soffice.py --headless --convert-to pdf output.pptx
rm -f slide-*.jpg
pdftoppm -jpeg -r 150 output.pdf slide
ls -1 "$PWD"/slide-*.jpg
```

**Pass the absolute paths printed above directly to the view tool.** The `rm` clears stale images from prior runs. `pdftoppm` zero-pads based on page count: `slide-1.jpg` for decks under 10 pages, `slide-01.jpg` for 10-99, `slide-001.jpg` for 100+.

**After fixes, rerun all four commands above** — the PDF must be regenerated from the edited `.pptx` before `pdftoppm` can reflect your changes.

## Dependencies

`pptxgenjs` (npm, preinstalled — install only if `require('pptxgenjs')` fails) · `python-pptx`, `Pillow`, `defusedxml`, `lxml` (pip — slide_text, thumbnail, clean, validate) · LibreOffice (`soffice`, auto-configured for sandboxed environments via `scripts/office/soffice.py`) · `pdftoppm` (Poppler)

---

## 질문

Base directory for this skill: /app/dstone/.claude/skills/respond-in-korean

# 한글로 응답하기

사용자에게 보이는 글은 **매 턴 전부 한글**로 쓴다. 선호 사항이 아니라 반드시 지켜야 하는 규칙이다.

## 왜

사용자가 같은 요청을 세 번 반복했다 (2026-09-19, 09-22, 09-23). 특히 세 번째에는 "진행상황"을 콕 집어 말했다. 영어로 새어 나가면 처음 하는 실수가 아니라 이미 지적받은 실수를 또 하는 것이다.

## 한글로 쓰는 것

- 도구를 여러 번 쓰는 긴 작업 중간의 진행 안내 (마지막 요약만이 아니다)
- 요약, 설명, 되묻는 질문
- 사용자가 영어로 써도, 작업 종류가 무엇이든(코딩, 인프라, 조사) 똑같이 적용

## 그대로 두는 것

- 코드, 파일 경로, 식별자, YAML/설정 값
- 커밋 메시지와 트레일러 (따로 지시가 없으면 원래 형식 유지)

## 보내기 전 확인

메시지를 보내기 직전에 본문이 한글인지 한 번 본다.

---

## 답변

먼저 샘플 템플릿과 Knowledge 화면 구성을 살펴보겠습니다.

---

## 답변

샘플 템플릿 분석은 끝났습니다. 지금은 Knowledge 화면을 캡처할 브라우저 도구(Chrome/puppeteer)를 찾는 중입니다.

---

## 답변

7081(dstone-boot)과 4081(dstone-knowledge)이 응답합니다(Windows 쪽 인스턴스로 보입니다). Knowledge 화면 구성 문서를 읽어 보겠습니다.

---

## 답변

Knowledge 메뉴 목록과 로그인 방식을 확인합니다.

---

## 답변

dstone-boot가 로그인을 요구합니다. 화면 캡처용 접근 방법을 찾아보겠습니다.

---

## 답변

PowerPoint가 Windows 쪽에 있어 슬라이드 시각 검수에 쓸 수 있겠습니다. 이제 화면 캡처용으로 보안을 끈 dstone-boot 사본을 7181 포트에 띄우겠습니다.

---

## 답변

Sitting tight while it boots — meanwhile I'll read the knowledge product doc for feature highlights.

---

## 답변

보안을 꺼도 Spring 기본 Basic 인증이 걸려서, 사본을 보안 자동 설정까지 빼고 다시 띄우는 중입니다.

---

## 답변

사본이 정상 동작합니다. 이제 headless Chrome으로 6개 메뉴 화면을 먼저 캡처해 보겠습니다.

---

## 답변

결과가 채워진 화면을 찍기 위해 각 화면의 조작 요소와 시연 데이터를 확인합니다.

---

## 답변

프로젝트·분석 화면의 결과 캡처를 확인하고 있습니다.

---

## 질문

[Image: original 660x5000, displayed at 264x2000. Multiply coordinates by 2.50 to map to original image.]

---

## 답변

요약 화면이 소개서에 쓰기 좋습니다(호출 해석률 99.96%). 다음은 검색 화면입니다.

---

## 답변

시연 프로젝트는 AI 소개서와 이어지도록 `testApp`(로그인 이력·게시판 등)을 쓰겠습니다. 검색·영향도 화면을 조작해 캡처합니다.

---

## 질문

[Image: original 1100x3190, displayed at 690x2000. Multiply coordinates by 1.59 to map to original image.]

---

## 답변

호출 관계 화면을 다시 찍는 중인데 페이지가 늦게 떠서 사본 서버 상태를 확인합니다.

---

## 답변

호출 관계도 캡처했습니다. 다음은 시각적으로 가장 강한 노드 맵입니다.

---

## 답변

HI-Japan 노드 맵이 자동으로 먼저 불러와지면서 화면이 잠기는 문제여서, 프로젝트 목록 응답에서 고객 프로젝트를 빼도록 캡처 스크립트를 고칩니다.

---

## 답변

노드 맵·관계 트리 캡처는 확보했고, 관계 트리를 더 크게 보이게 다시 찍고 SQL 보기 팝업까지 캡처합니다.

---

## 답변

관계 트리와 소스 팝업 확보했습니다. 이제 리비전 비교(testApp #43 → #58, AI가 FAQ를 추가한 변경)를 캡처합니다.

---

## 답변

리치클라이언트(WebSquare) 화면과 대규모 프로젝트 지도 캡처를 확인 중입니다.

---

## 답변

캡처는 충분히 모였습니다. 소개서에 넣을 수치(분석 시간·증분 분석 효과 등)를 문서에서 확인합니다.

---

## 답변

시연 수치(anybiz_prd: 파일 1,142개, 전체 분석 약 4분, 호출 해석률 99.96%)를 확보했습니다. 이제 샘플 템플릿 디자인을 PowerPoint로 렌더링해 살펴봅니다.

---

## 답변

PowerPoint 렌더링이 동작합니다. 템플릿 슬라이드를 몇 장 보고 디자인을 맞추겠습니다.

---

## 답변

템플릿 스타일(어두운 표지, 흰 바탕 본문, 눈썹 라벨+굵은 제목, 번호 동그라미 콜아웃)을 파악했습니다. 글꼴·색상 값을 확인합니다.

---

## 답변

슬라이드 구성(약 27장)을 정했습니다. 증분 분석·대용량 관련 수치를 하나 더 확인합니다.

---

## 답변

이미지 크롭을 준비합니다.

---

## 질문

[Image: original 2080x1020, displayed at 2000x981. Multiply coordinates by 1.04 to map to original image.]

---

## 답변

관계 트리 그림이 너무 작아서, 고해상도로 트리 부분만 다시 캡처합니다.

---

## 답변

고해상도 트리 띠 이미지를 확보했습니다. 이제 pptxgenjs로 27장짜리 소개서를 생성하는 스크립트를 작성합니다.

---

## 답변

27장 덱이 생성되고 검증을 통과했습니다. PowerPoint로 렌더링해 시각 검수합니다.

---

## 답변

전체적으로 잘 나왔지만 제목이 가운데 정렬로 나오고, 표지 겹침·몇몇 카드 넘침이 있어 고칩니다.

---

## 질문

[Image: original 1600x2250, displayed at 1422x2000. Multiply coordinates by 1.13 to map to original image.]

---

## 답변

수정이 반영됐습니다. 최종 파일을 저장하고 검증한 뒤 임시 서버를 정리합니다.

---

## 답변

소개서를 만들어 두었습니다: `/app/dstone/docs/temp/02.KNOWLEGE(분석Tool).pptx` (27장, 약 10MB)

샘플 템플릿(`01.AI에이전트-FOR-SM.pptx`)의 형식을 그대로 따랐습니다. 화면 비율, 맑은 고딕, 색상, 어두운 표지와 마지막 장, 영문 라벨 아래 굵은 제목, 화면 캡처 위 주황 번호와 오른쪽 설명 목록까지 같은 방식입니다. 파일 검증을 통과했고, PowerPoint로 27장을 모두 이미지로 렌더링해 겹침과 넘침을 확인한 뒤 고쳤습니다.

**구성 (27장)**
- **소개 (1~5)**: 표지, 유지보수 현장의 고민, 해결 방식, 실측 수치, 질문별 사용 예
  - 실측 수치는 실제 운영 웹앱 기준입니다. 파일 1,142개(약 37만 줄)를 3분 52초에 분석했고, 호출 99.96%의 대상을 확정했으며, 진입점(외부에서 들어오는 주소 등) 1,715개를 찾았습니다.
- **분석 준비 (6~10)**: 콘솔 메뉴, 프로젝트 등록과 분석, 분석 9단계, 리비전 요약, 임베딩 현황
- **찾기와 영향도 (11~15)**: 뜻으로 찾는 검색, 문서 올리기, 호출 관계, 영향도 분석, 신뢰도(HIGH/MEDIUM/LOW)
- **구조 시각화 (16~22)**: 노드 맵, 대규모 시스템 지도, 노드 상세, 관계 트리, 소스 보기, 테이블 한글명과 컬럼별 CRUD, WebSquare·Nexacro 지원
- **운영과 확장 (23~27)**: 리비전 비교, 사용 안내, AI 에이전트 연동(KnowledgeTool), 보안과 구성, 도입 효과와 절차

**화면 캡처 방법**
- 실제 콘솔을 headless Chrome으로 조작해 결과가 채워진 화면을 찍었습니다.
- 시연 데이터는 AI 소개서와 이야기가 이어지도록 `testApp`(로그인 이력 화면 → 테이블)을 중심으로 했습니다. 규모를 보여 주는 장에는 `anybiz_prd`, 리치클라이언트 장에는 `websquare-app`을 썼습니다.
- 고객 프로젝트 `HI-Japan`은 모든 캡처에서 목록과 선택지에서 빼고 찍었습니다.
- 7081은 로그인이 필요해서, 빌드된 war 사본을 임시 폴더에서 7181 포트로 보안만 끄고 띄워 캡처했습니다. 작업이 끝나 종료했고 저장소 코드나 설정은 바꾸지 않았습니다.

**외부에 배포하기 전에 확인해 주세요**
1. 캡처에 `testApp`의 패키지명 `kr.co.gnx…`과 테이블명(`TBSY_LOGIN_HIST` 등)이 그대로 보입니다. 4·17장의 실측 수치는 "운영 서버에서 받아 온 실제 앱"인 `anybiz_prd` 기준이고, 프로젝트명은 소개서에 적지 않았습니다. 이 이름과 수치를 외부에 공개해도 되는지 판단해 주세요.
2. `testApp` 최신 리비전에는 AI 작업 사본(`workshop/…`) 경로가 섞여 있습니다. 그래서 리비전 비교 화면의 "생김" 건수(파일 871개 등)는 이 사본 때문에 부풀려진 값입니다. 슬라이드 본문에는 이 숫자를 쓰지 않았지만 캡처에는 보입니다.
3. 파일명은 요청하신 대로 `KNOWLEGE`로 두었습니다. `KNOWLEDGE`가 맞다면 이름만 바꾸면 됩니다.

`docs/temp`는 git 관리 대상이 아니라 커밋하지 않았습니다.

---

## 질문

<pasted_content id="3019">
AI에이전트-FOR-SM 문서도 KNOWLEGE(분석Tool) 만큼의 수준으로 품질을 높여줘. 지금은 조금 설명이 빈약한 것 같아.

<문서위치>
/app/dstone/docs/temp/01.AI에이전트-FOR-SM.pptx
/app/dstone/docs/temp/02.KNOWLEGE(분석Tool).pptx
</pasted_content id="3019">

---

## 답변

먼저 현재 AI 에이전트 소개서가 어떻게 만들어졌는지(생성 스크립트가 남아 있는지)와 슬라이드별 구성을 확인하겠습니다.

---

## 답변

sm-workflow의 단계 구성과 갈림길, 엔진 기능(Skill·Sub Agent·Provider·필수 Tool 등)을 파악했습니다. 기존 덱에 들어 있는 이미지도 확인합니다.

---

## 답변

실행 관리 화면의 상세 영역을 캡처했고, 지금 결과를 확인하고 있습니다.

---

## 질문

[Image: original 1100x2233, displayed at 985x2000. Multiply coordinates by 1.12 to map to original image.]

---

## 답변

메시지가 전송되지 않았습니다. 전송 조건을 확인합니다.

---

## 답변

Meanwhile, I'll look at the gridded originals to place the number markers.

---

## 답변

채팅 응답을 기다리는 동안 sm-workflow의 승인 단계 갈림길을 확인합니다.

---

## 답변

응답이 오기 전에 캡처되어, 응답이 끝날 때까지 기다리도록 고쳐 다시 실행합니다.

---

## 답변

채팅이 다시 도는 동안 새 캡처를 다듬습니다.

---

## 답변

Now the body of the AI deck script.

---

## 답변

채팅 응답이 화면에 오지 않습니다. 엔진에 직접 요청해 원인을 확인합니다.

---

## 답변

CASE 장 4개를 원본에서 그대로 옮겨 붙입니다.

---

## 답변

Render for visual QA.

---

## 답변

몇 군데(차트 라벨 잘림, 채팅 장의 4번 항목 가림, 칩 넘침 등)를 고칩니다.

---

## 답변

결과가 깔끔합니다. 원본에 발표자 노트가 있었는지 확인한 뒤 저장하겠습니다.

---

## 답변

`01.AI에이전트-FOR-SM.pptx`를 Knowledge 소개서 수준으로 보강해 같은 경로에 덮어썼습니다. 22장이던 덱이 31장이 됐습니다. 원본은 `docs/temp/01.AI에이전트-FOR-SM(보강전).pptx`로 남겨 두었습니다.

파일 검증을 통과했고, PowerPoint로 31장을 모두 렌더링해 겹침과 넘침을 확인한 뒤 고쳤습니다.

**보강 방식**
- **기존 장**
  - 제목 아래에 안내 문장을 넣었습니다.
  - 화면 캡처 위에 주황 번호를 찍고, 오른쪽 설명과 번호를 맞췄습니다(디자이너, Agent 편집기, 실행 상세, Workflow 테스트).
  - 설명 문장을 늘렸습니다.
  - 승인 장에는 sm-workflow의 승인 지점 4곳과, 각각에서 고를 수 있는 길(승인·재분석·재설계·중지 등)을 표로 더했습니다.
- **CASE 1·2 (7~10장)**: PowerPoint에서 직접 만드신 장이라 손대지 않고 원본 그대로 옮겨 붙였습니다.

**새로 넣은 장 (9장)**

| 장 | 내용 |
|---|---|
| 4 동작 원리 | Workflow → Step → Agent → Tool을 회사 결재 라인에 빗대어 설명. "순서는 Java가, 판단은 LLM이" |
| 6 실행 한 건 따라가기 | 실제 실행 기록으로 만든 단계별 소요 시간 막대 차트(합계 12분 14초)와 사람이 내린 결정 4번 |
| 12 채팅 | Agent 선택, RAG·Tool 켜고 끄기, 모델 바꿔 보기 |
| 14 15단계 상세 | step00~14를 종류별 색으로 나누고 단계마다 만드는 산출물을 표시 |
| 16 실행 목록 | 실행 상태 5가지의 뜻 |
| 18 산출 문서 목록 | 어느 step이 쓰고 어느 step이 읽었는지, 문서 00~03-3 |
| 27 확장 | Skill · Sub Agent · 백그라운드 · MCP |
| 29 Knowledge 연동 | Agent별로 쓰는 knowledge Tool. 02 소개서로 연결 |
| 30 구축·보안 | 사내 모델, 폐쇄망, API 키·호출 제한, DB 기록, REST API, 컨테이너 배포 |

새 화면은 지난번처럼 7181 포트에 띄운 dstone-boot 사본에서 실제 엔진(8081) 데이터로 캡처했습니다. 사본은 작업이 끝나 종료했습니다.

**확인해 주실 것**
1. **채팅 장은 답변이 없는 화면입니다.** 대화 예시를 넣으려고 했지만, 보안을 끈 사본에서는 채팅 전송이 서버 예외로 끝나 답변을 받지 못했습니다. 그래서 장면을 꾸며 넣지 않고, 대화 설정(Agent 선택·옵션) 화면만 실었습니다. 정상 7081 화면에서 실제 대화를 캡처해 주시면 그 이미지로 바꿔 넣겠습니다.
2. **CASE 장 캡처에 "KB라이프파트너스" 로고가 보입니다.** 원본에 있던 것이라 그대로 두었습니다. 외부에 배포하기 전에 괜찮은지 확인해 주세요.
