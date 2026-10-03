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
 *   budget       logical surfaces        5   (this file's length)
 *   fork count   files mounting the primitive   3   (distinct `recipe` values)
 *   concurrency  primitives in the DOM at once  3   (measured at the real
 *                surface with the `verify` skill, never by regex — recorded
 *                here so the number has one home)
 *
 * Explicitly **not** instances, and therefore absent by design: environment
 * layers (wallpaper, atmosphere, ambient light, the reveal wake), the
 * navigation indicator *light* (a light on an existing slab), `.glass-material`
 * control skins, tier variants, and the backdrop remap.
 *
 * Amendment A1 (2026-10-02) added the first surface that is not a recipe's
 * only role: the landing dock's selection lens, a second slab mounted by the
 * dock recipe in the same host. A surface is therefore identified by
 * (recipe, host, role); the budget rose 4 → 5 and the login stage's
 * concurrency 2 → 3 (sign-in slab, dock, lens).
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
  /**
   * What the slab is for inside its recipe. `slab` is a recipe's own body; a
   * recipe that mounts the primitive a second time for another role (A1: the
   * dock's lens) names it, and that is a second surface in the same host.
   */
  role: 'slab' | 'lens'
  /** The declared rank (`glass.css` preset). */
  preset: MaterialPreset
  /** What proves the host mounts it — a substring, or a pattern when a prop decides. */
  mount: string | RegExp
  /** When it is on screen — the concurrency argument in words. */
  presence: string
}

export const MATERIAL_SURFACES: ReadonlyArray<MaterialSurface> = [
  {
    id: 'landing-dock',
    recipe: 'components/experience/GlassDock.vue',
    host: 'views/LoginView.vue',
    role: 'slab',
    preset: 'chrome',
    mount: '<GlassDock',
    presence: 'permanent on /login, across all three galleries',
  },
  {
    id: 'landing-dock-lens',
    recipe: 'components/experience/GlassDock.vue',
    host: 'views/LoginView.vue',
    role: 'lens',
    preset: 'chrome',
    mount: /<GlassDock[^>]*\slens[\s>]/,
    presence: 'permanent on /login with the dock: the selection lens over the current gallery',
  },
  {
    id: 'app-dock',
    recipe: 'components/experience/GlassDock.vue',
    host: 'layouts/AppLayout.vue',
    role: 'slab',
    preset: 'chrome',
    mount: '<GlassDock',
    presence: 'permanent in the authenticated shell at ≤768px; not mounted above it',
  },
  {
    id: 'sign-in-slab',
    recipe: 'views/LoginView.vue',
    host: 'views/LoginView.vue',
    role: 'slab',
    preset: 'hero',
    mount: '<GlassSurface',
    presence: 'permanent on /login; recessed but still mounted behind a gallery',
  },
  {
    id: 'note-selection-toolbar',
    recipe: 'features/notes/editor/NoteSelectionToolbar.vue',
    host: 'features/notes/editor/NoteEditor.vue',
    role: 'slab',
    preset: 'floating',
    mount: '<NoteSelectionToolbar',
    presence: 'while a text selection exists in the note editor',
  },
]

/**
 * Logical material surfaces. Raised 3 → 4 in Phase B5 for the app dock
 * (decision B), and 4 → 5 by Amendment A1 for the landing dock's lens.
 */
export const MATERIAL_BUDGET = 5

/** Files that mount the primitive. Unchanged by B5 and A1 — both add roles, not files. */
export const MATERIAL_FORK_COUNT = 3

/**
 * Primitives simultaneously in the DOM on any one screen. Measured, not
 * derived. 2 → 3 with A1: the login stage holds the sign-in slab, the dock
 * and its lens.
 */
export const MATERIAL_CONCURRENCY_CEILING = 3
