This is a Kotlin Multiplatform project targeting Desktop (JVM), Web, built with the [Kotlin Toolchain](https://kotlin-toolchain.org/dev/).

- [/desktopApp](./desktopApp) contains the desktop (JVM) application.
- [/shared](./shared) holds the code shared across your applications — Compose UI, business logic, and platform-specific implementations. [src](./shared/src) is for common code; the sibling `src@<platform>` folders (for example `src@android`) hold code compiled only for the platform named in the folder.
- [/webApp](./webApp) contains the web application, compiled to WebAssembly with Kotlin/Wasm.

The `kotlin` (macOS/Linux) and `kotlin.bat` (Windows) scripts in the project root are self-bootstrapping wrappers for the Kotlin Toolchain: they download the pinned toolchain version on first use, so no separate installation is required. Build the whole project with `./kotlin build`.

### Running

Use the run configurations provided by the run widget in your IDE's toolbar. You can also run each module from the command line:

- Desktop app: `./kotlin run -m desktopApp`
- Web app: `./kotlin run -m webApp`

### Timeline workspace

The shared Compose UI follows `requirements/code.html` and `requirements/DESIGN.md`:
compact metadata/KPI panels, 24dp process rows, pinned track labels, filled CPU/disk
plots, and category-colored process bars. A draggable horizontal scrollbar pans
the ruler, telemetry, and process bars together; zoom increases the scroll range.
Collapse the metadata banner or expand **Analysis & capabilities** as needed.

Bars show estimated `/proc` start time (assuming 100 Hz ticks) to last observation,
not confirmed process exit. Start/end labels appear inside each bar or alongside
very short bars; hover and selection expose the full range. Category presets
(All, Kernel, Native Daemons, Framework, Apps) combine with text/PID search and
duration thresholds (Off, >50ms, >200ms, >1s). Categories are name/parent heuristics;
unrecognized processes remain in All. Critical Path is disabled without causal
events; the UI does not reproduce the prototype's fabricated bottleneck claims.

Boot completion uses the user-selected SystemUI heuristic: the earliest sample
containing the exact process name `com.android.systemui`. The KPI and milestone
show **BOOT COMPLETED (EST.)**, not a verified Android completion broadcast.
The time is first observation, not process birth; restarts do not move it.
If SystemUI is absent, completion stays unknown. Capture end remains separate
in the metadata panel, and playback still spans the full capture.

Inter (400/500/600/700) and JetBrains Mono (400/500/600) are bundled as TrueType
Compose resources in `shared/composeResources/font`, downloaded from Google Fonts
(Inter v20 and JetBrains Mono v24, matching the reference families). No runtime
Google Fonts/CDN connection is needed. Their SIL Open Font License notices are
packaged in `shared/composeResources/files/licenses`.

After UI changes, run `./kotlin build` and `./kotlin test -m shared`, then visually
check desktop and web at different window widths, including horizontal scrolling,
zoom, combined filters, short-bar time labels, and dark/light themes.

### Resource imports after a package rename

Kotlin Toolchain 0.13.0 can reuse prepared Compose resources under the previous
package after `settings.compose.resources.packageName` changes. This leaves `Res`
in the new package but generates no font accessors, causing unresolved font imports.
Invalidate `build/generated/shared/common/preparedComposeResources` and its
resource-preparation and accessor-generation state files in
`build/incremental.state`, then run `./kotlin build` to regenerate them.
Keep the bundled fonts and new resource imports; do not edit generated accessors.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html), [Kotlin Toolchain](https://kotlin-toolchain.org/dev/), [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/), [Kotlin/Wasm](https://kotl.in/wasm/).
