---
name: Midnight Gold
colors:
  surface: '#131313'
  surface-dim: '#131313'
  surface-bright: '#3a3939'
  surface-container-lowest: '#0e0e0e'
  surface-container-low: '#1c1b1b'
  surface-container: '#201f1f'
  surface-container-high: '#2a2a2a'
  surface-container-highest: '#353534'
  on-surface: '#e5e2e1'
  on-surface-variant: '#d0c5af'
  inverse-surface: '#e5e2e1'
  inverse-on-surface: '#313030'
  outline: '#99907c'
  outline-variant: '#4d4635'
  surface-tint: '#e9c349'
  primary: '#f2ca50'
  on-primary: '#3c2f00'
  primary-container: '#d4af37'
  on-primary-container: '#554300'
  inverse-primary: '#735c00'
  secondary: '#ffe2ab'
  on-secondary: '#402d00'
  secondary-container: '#ffbf00'
  on-secondary-container: '#6d5000'
  tertiary: '#d0cdcd'
  on-tertiary: '#303030'
  tertiary-container: '#b4b2b2'
  on-tertiary-container: '#454545'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffe088'
  primary-fixed-dim: '#e9c349'
  on-primary-fixed: '#241a00'
  on-primary-fixed-variant: '#574500'
  secondary-fixed: '#ffdfa0'
  secondary-fixed-dim: '#fbbc00'
  on-secondary-fixed: '#261a00'
  on-secondary-fixed-variant: '#5c4300'
  tertiary-fixed: '#e4e2e1'
  tertiary-fixed-dim: '#c8c6c5'
  on-tertiary-fixed: '#1b1c1c'
  on-tertiary-fixed-variant: '#474746'
  background: '#131313'
  on-background: '#e5e2e1'
  surface-variant: '#353534'
typography:
  display-lg:
    fontFamily: Bodoni Moda
    fontSize: 64px
    fontWeight: '600'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Bodoni Moda
    fontSize: 40px
    fontWeight: '600'
    lineHeight: '1.2'
  headline-md:
    fontFamily: Bodoni Moda
    fontSize: 32px
    fontWeight: '500'
    lineHeight: '1.3'
  headline-sm:
    fontFamily: Bodoni Moda
    fontSize: 24px
    fontWeight: '500'
    lineHeight: '1.4'
  body-lg:
    fontFamily: Manrope
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Manrope
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.6'
  label-caps:
    fontFamily: Manrope
    fontSize: 12px
    fontWeight: '600'
    lineHeight: '1.0'
    letterSpacing: 0.1em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  base: 8px
  xs: 0.5rem
  sm: 1rem
  md: 2rem
  lg: 4rem
  xl: 8rem
  gutter: 24px
  margin-desktop: 64px
  margin-mobile: 20px
---

## Brand & Style
The design system embodies "Neo-Luxury"—a fusion of timeless editorial elegance and modern digital precision. It is designed for high-end lifestyle, luxury retail, or exclusive concierge services where the goal is to evoke a sense of prestige, stillness, and curated sophistication. 

The visual style leans into **Minimalism** with **Glassmorphism** accents. It utilizes deep, immersive dark surfaces to allow photography and gold accents to command attention. The interface should feel like a high-end physical lookbook: spacious, intentional, and quiet.

## Colors
The palette is anchored by **Onyx (#0A0A0A)**, providing a void-like depth that serves as the canvas. 

- **Primary (Champagne Gold):** Used for key brand moments, primary actions, and decorative elements. It should be applied sparingly to maintain its value.
- **Secondary (Soft Amber):** Reserved for hover states, subtle highlights, and active indicators to provide warmth without breaking the monochromatic depth.
- **Neutral/Surface:** Tiers of charcoal are used to define depth. Avoid pure black for UI containers; use subtle shifts in grey to indicate hierarchy.

## Typography
The typographic system relies on extreme contrast between the serif and sans-serif families.

- **Headlines:** Bodoni Moda provides an editorial, high-fashion feel. Use "Display" optical sizes for large hero sections.
- **Body:** Manrope offers a clean, technical, and highly legible counterpoint to the serif, ensuring the interface remains functional and modern.
- **Labels:** Use uppercase tracking for small labels to evoke the feel of premium watchmaking or architectural blueprints.

## Layout & Spacing
This design system uses a **Fixed Grid** approach for desktop to preserve editorial compositions, while transitioning to a **Fluid Grid** for mobile. 

- **Generous Whitespace:** Padding and margins are intentionally oversized (64px+) to create a "gallery" feel.
- **Grid:** A 12-column system is used. Content should often be offset or centered within 8 columns to create a balanced, asymmetrical look common in luxury magazines.
- **Rhythm:** Spacing follows a geometric progression (8, 16, 32, 64, 128). Use `xl` spacing to separate major narrative sections.

## Elevation & Depth
Depth is created through **Tonal Layering** and **Glassmorphism** rather than traditional heavy shadows.

- **Surface Levels:** The background is #0A0A0A. Floating cards or navigation bars use a semi-transparent charcoal (approx. 60% opacity) with a `24px` backdrop blur.
- **Shadows:** Use extremely soft, diffuse "Ambient Shadows" with a slight amber tint (`rgba(212, 175, 55, 0.05)`) to make elements feel as though they are glowing softly against the dark background.
- **Borders:** Use 1px "Ghost Borders" in a low-opacity gold or silver to define edges without adding visual bulk.

## Shapes
Shapes are disciplined and architectural. While the base roundedness is set to **Soft (0.25rem)**, specific elements follow these rules:
- **Buttons & Inputs:** Use the base 4px (Soft) radius to maintain a crisp, professional edge.
- **Image Containers:** May use 0px (Sharp) corners for a more "fine art" editorial look.
- **Interactive Tags:** May use a full pill shape to distinguish them from structural elements.

## Components
- **Buttons:** Primary buttons are Champagne Gold with black text. Secondary buttons are "Ghost" style—transparent with a 1px gold border. Hover states should include a subtle outer glow.
- **Cards:** Cards use a glassmorphic background with a very subtle 1px border (#FFFFFF 10%). Padding inside cards should be generous (min 32px).
- **Inputs:** Dark backgrounds with a simple underline or bottom border in gold that animates to full width on focus. Labels should use the `label-caps` style.
- **Chips:** Small, pill-shaped elements with a low-opacity gold fill and high-contrast gold text.
- **Specialty Component - The Divider:** Instead of a simple line, use a thin, centered 1px gold line that fades out at both ends to separate major sections.