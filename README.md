This is a Kotlin Multiplatform project targeting Desktop (JVM), Web, built with the [Kotlin Toolchain](https://kotlin-toolchain.org/dev/).

- [/desktopApp](./desktopApp) contains the desktop (JVM) application.
- [/shared](./shared) holds the code shared across your applications — Compose UI, business logic, and platform-specific implementations. [src](./shared/src) is for common code; the sibling `src@<platform>` folders (for example `src@android`) hold code compiled only for the platform named in the folder.
- [/webApp](./webApp) contains the web application, compiled to WebAssembly with Kotlin/Wasm.

The `kotlin` (macOS/Linux) and `kotlin.bat` (Windows) scripts in the project root are self-bootstrapping wrappers for the Kotlin Toolchain: they download the pinned toolchain version on first use, so no separate installation is required. Build the whole project with `./kotlin build`.

### Running

Use the run configurations provided by the run widget in your IDE's toolbar. You can also run each module from the command line:

- Desktop app: `./kotlin run -m desktopApp`
- Web app: `./kotlin run -m webApp`


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
