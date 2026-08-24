# Fmhi

[![License](https://img.shields.io/badge/License-MIT-2563eb?style=for-the-badge)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-25%2B-f59e0b?style=for-the-badge&logo=openjdk&logoColor=white)](https://jdk.java.net/25/)
[![Gradle](https://img.shields.io/badge/Gradle-9.x-02303a?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org)

**Fmhi is a modular, backend-agnostic application framework for the modern JVM.**
It gives you GPU-accelerated 2D and 3D rendering, spatial audio, high-quality
text layout, and a full widget toolkit — each as an independent layer you can
take or leave. Backends are swappable jars discovered at runtime; your code only
ever sees the abstraction. No global singletons, no annotation magic, no
hard-wired platform assumptions. Just composable libraries that get out of your
way.

---

## Why Fmhi

### Pluggable backends — your code owns the abstraction

Most frameworks ship with a hard-wired rendering backend. If the API exposes
OpenGL types, you write OpenGL code. If you later want Vulkan, you rewrite your
renderer. If you want a headless mode for CI or server-side rendering, you
simulate a GPU — or give up.

Fmhi separates **what you want to do** from **how it gets done.** `fmhi-gfx`
defines the GPU abstraction; `fmhi-opengl` is one implementation. Swap it for a
Vulkan or null backend by dropping a different jar on the runtime classpath.
Your application code imports only the API module and never touches a backend
type.

This means you can:

- **Test rendering in CI** with a zero-dependency null backend.
- **Ship a native-feeling Metal renderer on macOS** without changing your
  pipeline code.
- **Start on OpenGL, move to Vulkan** when you need bindless resources or ray
  queries — same application code, different runtime jar.

### A layered stack, not a monolith

Fmhi is organized as a strict layered graph. Each module depends only on the
layer directly beneath it. You pull in exactly what your application needs, and
the dependency graph is shallow enough to reason about in your head.

### API design as a first principle

Every public API in Fmhi follows a small set of rules:

- **Constructor injection over global state** — receive a `Graphics` or
  `AudioContext` as a parameter. No static singletons, no ambient context. Your
  code is testable with mocks or stubs by construction.
- **Fluent defaults on interfaces** — common operations ship as `default`
  methods so implementations stay focused on the essential.
- **Composition over inheritance** — public APIs are interfaces. Shared
  behavior lives in composed utility classes, not base classes.
- **Immutable by default** — value types are records. Configuration is
  constructed once and passed forward.

These rules aren't aspirational. They're enforced by the module boundaries —
you physically cannot import a backend type from an API module.

### Pragmatic scope

Fmhi targets **desktop and server JVM.** It does not attempt to run on
Android, iOS, or in a web browser. That's a trade-off, not an oversight: by
letting go of platforms where Java is constrained, the framework gets to use
the full modern JDK on the platforms where Java excels.

---

## Quick Start

**Prerequisites:** JDK 21+

```bash
git clone https://github.com/licphel/fmhi.git
cd fmhi
./gradlew publishAllPublicationsToLocalRepository
```

Then in your project:

```groovy
repositories {
    maven { url = 'file:///path/to/fmhi/build/repo' }
}

dependencies {
    implementation 'net.fmhi:fmhi-core:1.0.0'
    implementation 'net.fmhi:fmhi-gfx:1.0.0'
    runtimeOnly    'net.fmhi:fmhi-opengl:1.0.0'
    runtimeOnly    'net.fmhi:fmhi-glfw:1.0.0'
}
```

> API modules on `implementation`; backends on `runtimeOnly`. Your code
> imports the abstraction — never the implementation.

---

## Building from source

```bash
./gradlew build
```

All modules compile with `-Xlint:all -Werror`. Zero warnings in the main
source tree.

---

## License

MIT — see [LICENSE](./LICENSE).
