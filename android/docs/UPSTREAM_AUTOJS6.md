# AutoJs6 upstream provenance

- Repository: <https://github.com/SuperMonster003/AutoJs6.git>
- Pinned commit: `ed3eb10e88db5a8425fd94bdddefa4176e5e1c94`
- Imported version: `6.7.0`
- License: Mozilla Public License 2.0; the complete text is preserved in
  [`android/LICENSE`](../LICENSE).

## Local modifications policy

The imported `android/` tree remains traceable to the pinned upstream commit.
Assistant-specific behavior should be isolated in the `assistant` product flavor
and `app/src/assistant/` source set where practical. Any later change to upstream
files must be documented with its purpose and retained in version control; upstream
copyright, license, and attribution notices must not be removed.

Current upstream-file modification:

- `app/src/main/java/org/autojs/autojs/ui/log/LogBottomSheet.kt` replaces obsolete
  `AutoJs.getInstance()` calls with the pinned baseline's current `AutoJs.instance`
  API so the imported 6.7.0 sources compile.
- `app/src/main/AndroidManifest.xml` rewords one storage-permission comment so the
  assistant merged-manifest forbidden-token audit is semantic and returns no stale
  all-files permission token from comments.
- `app/src/main/java/org/autojs/autojs/AbstractAutoJs.kt` prevents the assistant
  channel from registering the root automator engine, initializing Shizuku, or
  creating a root shell while preserving the upstream behavior for other channels.
- `app/src/main/java/org/autojs/autojs/runtime/ScriptRuntime.kt` omits the root
  automator and Shizuku JavaScript APIs from the assistant channel.
- `app/src/main/java/org/autojs/autojs/App.kt` skips Inrt shortcut synchronization
  and LeakCanary launcher setup for the assistant channel because those inherited
  UI components are intentionally absent from the standalone manifest.

## MPL-2.0 file-level obligations

Files covered by MPL-2.0 remain under MPL-2.0. If executable form containing those
files is distributed, recipients must be informed how to obtain the corresponding
Source Code Form, including local modifications to covered files, and the license
and notices must remain available. New files may use a compatible chosen license,
but combining them with MPL-covered files does not remove the obligations that
apply to the covered files.
