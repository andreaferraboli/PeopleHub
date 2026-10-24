# Scene Velluto

Gli oggetti 3D e le copertine illustrate del redesign Velluto, convertiti in VectorDrawable.
La fonte sono gli SVG in `src/` (copiati dai mockup, con i colori dell'accento come segnaposto);
gli XML in `core/ui/src/main/res/drawable*/scene_*.xml` sono output e non si toccano a mano.

## Rigenerare

```bash
python3 tools/scenes/build_scenes.py --accent FFC83D
```

Cambiare accento vuol dire cambiare quel valore e rilanciare. Lo script ricava `g1..g5` con lo
stesso `mix()` dei mockup (g1 = 70% verso il bianco, g2 = 22% verso il bianco, g3 = accento,
g4 = 38% verso il nero, g5 = 66% verso il nero). Solo libreria standard di Python 3.
Opzioni: `--res <cartella>` per scrivere altrove, `--only scene_lock scene_pin` per un sottoinsieme.

`drawable/` contiene la variante chiara (default). Una scena va anche in `drawable-night/` solo se
il sorgente usa colori legati al tema (`{{glassFill}}`, `{{glassStroke}}`, valori in
`THEME_VARS`): oggi solo `scene_hourglass`.

## Asset

| Scena | Viewport | Da |
|---|---|---|
| scene_rings | 160x120 | People-Cerchia |
| scene_gift | 160x160 | People-Riflessione |
| scene_pin | 100x140 | People-Eventi |
| scene_hourglass | 140x180 (+ night) | People-Promemoria |
| scene_lock | 180x200 | People-Cassaforte |
| scene_cover_travel | 358x224 | People-Eventi |
| scene_cover_milestone | 358x210 | People-Eventi |
| scene_cover_dinner | 358x210 | People-Eventi |
| scene_cover_wedding | 358x196 | People-Eventi |
| scene_cover_anniversary | 358x196 | People-Eventi |
| scene_cover_home | 358x196 | People-Eventi |

## Cosa non c'e nei VectorDrawable (lo fa Compose)

- `filter: drop-shadow(...)` su tutti gli oggetti, i bagliori radiali e le ombre a terra (sono div
  separati dall'SVG nei mockup).
- Animazioni CSS. Gli elementi animati hanno un nome, dentro un `<group android:name="<nome>_group">`
  (con `pivotX/pivotY` = `transform-origin` dove c'era una scala):
  `scene_gift` sparkle_1 (pivot 138,34) e sparkle_2 (pivot 18,45), twinkle opacita .2-1 e scala .7-1;
  `scene_hourglass` sand_stream, opacita .55-1;
  `scene_lock` sheen, translateX -30..30 e opacita .15-.55;
  `scene_cover_milestone` star_1..star_3, `scene_cover_dinner` sparkle (opacita .35-1);
  `scene_cover_anniversary` flame (pivot 72,152, scaleY 1-1.12).
  Il drift della copertina viaggio (scala 1.04-1.08) e tutto in Compose.
- Lo scrim scuro sopra le copertine (`linear-gradient(rgba(8,6,5,...))`).
- Il filtro `saturate()/brightness()` delle card d'archivio (wedding .7/.9, anniversary .75/.92,
  home .75/.9): se serve, ColorMatrix in Compose.

## Conversioni da sapere

- Gradienti in objectBoundingBox convertiti in coordinate assolute in modo esatto: il valore del
  gradiente e affine nelle coordinate, quindi start/end sono scelti per riprodurre le stesse
  isolinee del browser anche su bbox non quadrati (dove "0,0 -> 1,1" non e la diagonale geometrica).
- Il radialGradient del quadrante del lucchetto sta su un cerchio (bbox quadrato): conversione esatta.
- Sfondo CSS delle copertine: un rettangolo a tutta area per strato (`bg_0` e il piu in basso), i
  radiali con raggio = ultimo stop e offset = stop/raggio.
- Stop trasparenti: CSS interpola in alfa premoltiplicato, Android no. Uno stop con alfa 0 prende
  l'RGB dello stop vicino, cosi la sfumatura non scurisce (`rgba(0,0,0,0)` diventa `#00RRGGBB`).
- Lo `skewX(-18)` della striscia di luce del lucchetto e applicato alle coordinate del path (e al
  suo gradiente). Nel browser l'animazione CSS sostituisce l'attributo `transform`, quindi nel
  mockup la striscia appare dritta: qui resta inclinata come scritto nell'SVG.
- Le copertine nei mockup usano `preserveAspectRatio="xMidYMid slice"`: in Compose
  `ContentScale.Crop`.
- La card "Evento fissato" di People-Riflessione (358x240) usa una variante del tramonto con
  sole e forme spostati di 10-16px: non c'e un asset a parte, si usa scene_cover_travel in Crop.

## Verifica

Ogni XML passa `aapt2 compile`. Per il controllo visivo si riconverte il VectorDrawable in SVG e
lo si rende accanto all'originale con Chromium headless (script fuori dal repo).
