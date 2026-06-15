---
name: Monochrome Utility
colors:
  surface: '#f9f9f9'
  surface-dim: '#dadada'
  surface-bright: '#f9f9f9'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3f3'
  surface-container: '#eeeeee'
  surface-container-high: '#e8e8e8'
  surface-container-highest: '#e2e2e2'
  on-surface: '#1a1c1c'
  on-surface-variant: '#4c4546'
  inverse-surface: '#2f3131'
  inverse-on-surface: '#f1f1f1'
  outline: '#7e7576'
  outline-variant: '#cfc4c5'
  surface-tint: '#5e5e5e'
  primary: '#000000'
  on-primary: '#ffffff'
  primary-container: '#1b1b1b'
  on-primary-container: '#848484'
  inverse-primary: '#c6c6c6'
  secondary: '#5f5e5e'
  on-secondary: '#ffffff'
  secondary-container: '#e5e2e1'
  on-secondary-container: '#656464'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#160066'
  on-tertiary-container: '#7f70fb'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e2e2e2'
  primary-fixed-dim: '#c6c6c6'
  on-primary-fixed: '#1b1b1b'
  on-primary-fixed-variant: '#474747'
  secondary-fixed: '#e5e2e1'
  secondary-fixed-dim: '#c8c6c5'
  on-secondary-fixed: '#1c1b1b'
  on-secondary-fixed-variant: '#474646'
  tertiary-fixed: '#e4dfff'
  tertiary-fixed-dim: '#c6bfff'
  on-tertiary-fixed: '#160066'
  on-tertiary-fixed-variant: '#4029ba'
  background: '#f9f9f9'
  on-background: '#1a1c1c'
  surface-variant: '#e2e2e2'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '500'
    lineHeight: 28px
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 4px
  gutter: 16px
  margin-mobile: 20px
  margin-desktop: 40px
  stack-sm: 8px
  stack-md: 16px
  stack-lg: 24px
---

## Brand & Style
This design system centers on a high-contrast, utility-first aesthetic tailored for modern fintech. It prioritizes clarity and efficiency, evoking a sense of "monetary precision." The target audience is the financially literate urban user who values speed and accuracy in communal expense management.

The style is **Minimalist with High-Contrast** accents. It leans into a "receipt-inspired" purity, using vast amounts of white space to make financial data legible at a glance. By stripping away decorative gradients and heavy colors, the interface feels like a sophisticated tool rather than a social app. The emotional response should be one of confidence, transparency, and clinical efficiency.

## Colors
The palette is intentionally restricted to maintain a premium, editorial feel. 

- **Foundation:** Pure White (#FFFFFF) is used for all primary backgrounds to ensure the highest possible contrast for financial figures.
- **Action & Ink:** Solid Black (#000000) is reserved for primary actions (buttons) and iconography. Near-black (#121212) is used for body text to reduce eye strain while maintaining a sharp appearance.
- **Surface:** Light Gray (#F5F5F5) defines cards and input containers, creating a subtle layer of depth without relying on shadows.
- **Functional Accents:** Subtle Green (#2ECC71) is used exclusively for positive financial outcomes (e.g., "Settle Up" or balance owed back). The Brand Purple (#6C5CE7) is a "precision needle"—used only for active indicators, notification pips, or small brand marks.

## Typography
This design system utilizes **Inter** across all levels to maintain a neutral, systematic appearance. 

The hierarchy is dominated by "Display" and "Headline" levels for currency amounts, ensuring users immediately identify the most important data point (the total). Use `label-sm` with uppercase styling for secondary metadata like "PAID BY" or "SPLIT WITH" to create a distinct structural rhythm. Financial figures should always use tabular lining (tnum) if available to ensure columns of numbers align perfectly.

## Elevation & Depth
Depth is primarily achieved through **Tonal Layers** rather than heavy drop shadows. 

- **Base Layer:** Pure White background.
- **Mid Layer:** Cards and containers use Light Gray (#F5F5F5) or subtle 1px outlines (#E0E0E0).
- **Interactive Layer:** When an element is pressed or elevated (like a bottom sheet), a very soft, diffused ambient shadow is used (0px 8px 24px rgba(0, 0, 0, 0.04)).
- **Dividers:** Use hairline 1px dividers (#E0E0E0) to separate list items without adding visual bulk.

## Shapes
The shape language combines generous corner radii with pill-shaped accents to soften the high-contrast aesthetic.

- **Cards & Sheets:** Use a radius of 24px (`rounded-xl`) to create a modern, friendly container feel.
- **Input Fields:** Use a radius of 12px to 16px to maintain consistency with cards.
- **Pills:** All chips, tags, and secondary action buttons use a full pill-shape (radius: 999px).
- **Avatars:** Strictly circular to contrast against the rectangular card geometry.

## Components
- **Buttons:** Primary buttons are solid black (#000000) with white text. Secondary buttons are light gray (#F5F5F5) with black text. Always use pill-shapes for small buttons and 12px rounded rectangles for full-width actions.
- **Input Fields:** Minimalist containers using the light gray surface color (#F5F5F5) with no border unless focused. Focus state uses a 2px black outline.
- **Cards:** White background with a 1px #E0E0E0 border or Light Gray background with no border.
- **Chips:** Small pill-shaped containers for categories or split types. Active states use a solid black background; inactive states use a subtle gray outline.
- **Avatars:** Circular icons featuring user initials. Each user should be assigned a distinct, muted background color (e.g., soft sage, dusty blue) to aid in quick visual recognition during splits.
- **Bottom Action Bar:** A floating or pinned container at the bottom of the screen that houses the primary call-to-action (e.g., "Add Expense" or "Confirm Split").