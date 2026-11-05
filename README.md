<h1 align="center">Kite for Android</h1>

<p align="center">
  <b>Write on the phone. Keep writing without a network. Sync back when there is one.</b>
</p>

<p align="center">
  The third surface of <a href="https://github.com/kite-plus/kite">Kite</a>: it does not re-implement rendering,<br>
  word count, preview or content types — it asks the server for those, and only carries what the author typed<br>
  into a local database and back into the folder on the desktop.
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/Amazon2025/kite-android?style=flat-square&color=4a77d6&label=license" alt="License"></a>
  <a href="gradle/libs.versions.toml"><img src="https://img.shields.io/badge/kotlin-2.2-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="app/build.gradle.kts"><img src="https://img.shields.io/badge/android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+"></a>
  <a href="docs/ROADMAP.md"><img src="https://img.shields.io/badge/status-M1%20in%20progress-4a77d6?style=flat-square" alt="Status"></a>
</p>

<p align="center">
  <a href="docs/ROADMAP.md"><b>Roadmap</b></a> ·
  <a href="docs/CONNECTION.md"><b>Connecting</b></a> ·
  <a href="https://github.com/kite-plus/kite"><b>Kite</b></a> ·
  English · <a href="README.zh-CN.md">简体中文</a>
</p>

## Why it exists

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>Write first, worry about the network later</h3>
      <p>Articles live in Room on the phone, and the screens read only that database. In the underground, with the desktop off, with the router unplugged, writing works the same. There is no save button: when your hands stop, the text is already stored.</p>
    </td>
    <td width="50%" valign="top">
      <h3>A conflict is never resolved for you</h3>
      <p>When the server says the version you saw is gone, both versions are kept: your text stays put, the other side is filed beside it, and the editor offers two choices. Nothing is overwritten and nothing is merged behind your back.</p>
    </td>
  </tr>
  <tr>
    <td valign="top">
      <h3>The studio's shape, on a phone</h3>
      <p>Categories, slug, status, scheduled publishing, and the fields the site's content types declare are all read from <code>/content-types</code> and drawn from that schema. Pictures upload straight to the server; a cover is written to <code>meta.cover</code>.</p>
    </td>
    <td valign="top">
      <h3>Preview and word count come from the server</h3>
      <p>Preview is <code>POST /preview</code>, which returns the page the site actually renders; the count is <code>POST /wordcount</code>. The client runs no Markdown library of its own, so it cannot drift from what will be published.</p>
    </td>
  </tr>
  <tr>
    <td valign="top">
      <h3>The client is generated from the contract</h3>
      <p>Client code comes from the server's <code>/api/v1/openapi.json</code>, which is built from the same table as the router. When the contract moves, re-run <code>tools/generate-api.sh</code>; a field that no longer matches fails at compile time, not as a blank spot at runtime.</p>
    </td>
    <td valign="top">
      <h3>Nothing but the client</h3>
      <p>Only existing <code>/api/v1</code> endpoints are used, and Kite itself is left alone. Anything that would need the server to change is written down first and waits for a decision. A background job syncs every six hours when there is a network.</p>
    </td>
  </tr>
</table>

## Quick start

Start the server on the desktop, bound to its address on the local network (by default it listens on the loopback only, where the phone cannot reach it):

```bash
kite run --addr 192.168.1.20:1717        # your desktop's own LAN address
```

On the phone: put the same address in Settings (**without** `/api/v1`, the app adds it) → tap “Test connection” → sign in with the account the studio uses → tap “Sync now”.

That address is handed out by DHCP and will change; when it does, type the new one. The local network is plain HTTP, so use it only on a network you trust, and do not expose port 1717 to the internet — Kite has no TLS yet.

## Build

The project builds with Gradle 9.5, AGP 9.3, Kotlin 2.2 and KSP2, on JDK 21 or newer (Android Studio's bundled `jbr` is the easy choice), with sources on Java 17. There is no `gradlew` or `gradle-wrapper.jar` in the tree: open the folder in Android Studio, or use a Gradle you already have.

```bash
gradle :app:testDebugUnitTest :api:testDebugUnitTest
gradle :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Dependencies come from the Aliyun mirrors and the Gradle distribution from Tencent's, both configured in `settings.gradle.kts`. After the server's contract changes, regenerate the client:

```bash
./tools/generate-api.sh          # needs a JDK; caches the generator jar, replaces api/src/main/java wholesale
```

## How it works

**The phone keeps its own copy.** Every article is in Room, screens read the local database and never wait for the network. The sync engine is the only thing that talks upwards.

**Push, then pull.** The other order would overwrite whatever you wrote offline.

| Case | What happens |
|---|---|
| Push | A new article is `POST`ed and takes the id the server gives back; an edit is `PUT` with the `If-Match` it was made against |
| Pull | `updated_from` asks only for what changed, paged by cursor; a listing carries no body, so known rows keep their local text and unseen ones are read whole before they are stored |
| Deletes | A separate pass with `deleted_only=true`; a row with unsent changes is kept and the watermark is held back |
| Conflict | On 409 both versions are kept for you to choose between; that row is not retried |
| Signed out / read only | 401 and 405 are told apart and neither is retried as a network fault |

## Layout

```
app/                         the app
├── ui/            Compose screens: list, editor, settings
├── data/          settings, repository, content types, discovery, uploads, server rendering
│   ├── local/     Room: ContentEntity / ContentDao / KiteDatabase
│   └── remote/    KiteGateway (the only place generated code is touched), cookie persistence
├── domain/        sync: SyncEngine orchestrates, Pusher / Puller / ConflictResolver do the work
└── work/          WorkManager background sync
api/                         the client generated from the OpenAPI document — do not hand-edit
```

A few rules: dependencies point one way (`ui → data → domain`, and `domain` imports no Android); the database is the only truth; conversions between models live in exactly two places, `KiteGateway` and `ContentMapping`; the spec is the contract.

## Tests

```bash
gradle :app:testDebugUnitTest
```

21 JVM tests, plus 2 for the generated client: sync (push, pull, conflict, deletion, watermark), repository (no dirty flag without an edit, meta merging, scheduled dates), discovery (which networks to sweep), uploads (the multipart field name and the 415), server rendering (preview and word count). There are no instrumented tests and no screenshot comparison — the interactions on a device are checked by hand, and that is where the claim “it passes” ends.

## Troubleshooting

<details>
<summary><b>It crashes and there is no stack trace</b></summary>

`CrashLog` writes uncaught exceptions to `/sdcard/Android/data/com.kiteplus.kite.mobile/files/logs/crash.txt`:

```bash
adb pull /sdcard/Android/data/com.kiteplus.kite.mobile/files/logs/crash.txt
```

With USB debugging, `adb logcat -c && adb logcat *:E` is quicker.

</details>

<details>
<summary><b>It cannot reach Kite on the desktop</b></summary>

1. Run `kite run`, not `kite serve` — serve only has the static site, so `/api/v1/*` is a 404 there.
2. Do not add `/api/v1` to the address; the app appends it.
3. Phone and desktop must be on the same network; guest networks usually enable client isolation, which blocks this.
4. On Windows, allow the app through the firewall for both private and public networks the first time it asks; when the network is “public”, a private-only rule does not apply.
5. The desktop's address comes from DHCP and will change; retype it, or pin the address on the router.

</details>

## Documentation

[Roadmap](docs/ROADMAP.md): milestones and engineering rules · [Connecting](docs/CONNECTION.md): what works today, the candidates, and the trade-offs

## License

[Apache-2.0](LICENSE), the same as Kite.
