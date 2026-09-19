import type { MaterialPreset } from '../materials'

/**
 * The material surface registry (Phase B5).
 *
 * The budget was a number with no instrument. `glassBudget.spec.ts` asserts the
 * set of *files* containing `<GlassSurface` — a **fork guard**, and a good one,
 * but blind to how many logical surfaces those files produce: a recipe mounted
 * in two hosts is one file and two surfaces, and until B5 nothing in the build
 * could tell the difference. This is that instrument.
 *
 * The unit, from `components.md` §1:
 *
 *   One budget instance = one logical material surface: a distinct role,
 *   identified by (recipe, host container), that mounts the refracting
 *   primitive — whether or not it is currently visible.
 *
 * Three numbers travel together, and all three are asserted from this table:
 *
 *   budget       logical surfaces        4   (this file's length)
 *   fork count   files mounting the primitive   3   (distinct `recipe` values)
 *   concurrency  primitives in the DOM at once  2   (measured at the real
 *                surface with the `verify` skill, never by regex — recorded
 *                here so the number has one home)
 *
 * Explicitly **not** instances, and therefore absent by design: environment
 * layers (wallpaper, atmosphere, ambient light, the reveal wake), the
 * navigation indicator (a light on an existing slab), `.glass-material` control
 * skins, tier variants, and the backdrop remap.
 *
 * It lives under `__tests__/` deliberately: every guard's source walk skips
 * that directory, so the registry can name files without being mistaken for
 * one of them, and nothing test-only reaches the bundle.
 */
export interface MaterialSurface {
  /** Stable id for the role, used in failure messages. */
  id: string
  /** The file that mounts the primitive — must be allow-listed by the fork guard. */
  recipe: string
  /** The container that gives the surface its role. Two hosts = two surfaces. */
  host: string
  /** The declared rank (`glass.css` preset). */
  preset: MaterialPreset
  /** The token that proves the host mounts it. */
  mount: string
  /** When it is on screen — the concurrency argument in words. */
  presence: string
}

export const MATERIAL_SURFACES: ReadonlyArray<MaterialSurface> = [
  {
    id: 'landing-dock',
    recipe: 'components/experience/GlassDock.vue',
    host: 'views/LoginView.vue',
    preset: 'chrome',
    mount: '<GlassDock',
    presence: 'permanent on /login, across all three galleries',
  },
  {
    id: 'app-dock',
    recipe: 'components/experience/GlassDock.vue',
    host: 'layouts/AppLayout.vue',
    preset: 'chrome',
    mount: '<GlassDock',
    presence: 'permanent in the authenticated shell at ≤768px; not mounted above it',
  },
  {
    id: 'sign-in-slab',
    recipe: 'views/LoginView.vue',
    host: 'views/LoginView.vue',
    preset: 'hero',
    mount: '<GlassSurface',
    presence: 'permanent on /login; recessed but still mounted behind a gallery',
  },
  {
    id: 'note-selection-toolbar',
    recipe: 'features/notes/editor/NoteSelectionToolbar.vue',
    host: 'features/notes/editor/NoteEditor.vue',
    preset: 'floating',
    mount: '<NoteSelectionToolbar',
    presence: 'while a text selection exists in the note editor',
  },
]

/** Logical material surfaces. Raised 3 → 4 in Phase B5 for the app dock (decision B). */
export const MATERIAL_BUDGET = 4

/** Files that mount the primitive. Unchanged by B5 — the app dock is a second host. */
export const MATERIAL_FORK_COUNT = 3

/** Primitives simultaneously in the DOM on any one screen. Measured, not derived. */
export const MATERIAL_CONCURRENCY_CEILING = 2
