# Enable Android CI

`android.yml` is a complete workflow template pinned to checked upstream action commits. It has been validated with actionlint. It builds the debug app and test APK, runs JVM tests and Android lint, and uploads the APK/reports.

It is intentionally outside `.github/workflows/`: GitHub rejected publishing a workflow with the available OAuth credential because it lacks the `workflow` scope. The repository source and preview release can be published with that credential.

A maintainer with workflow-write permission can activate it:

```sh
mkdir -p .github/workflows
git mv ci/android.yml .github/workflows/android.yml
git commit -m "Enable Android CI"
git push
```

Use a credential authorized to write workflows (for a classic OAuth token, the `workflow` scope). This template does not contain secrets or require signing keys. After activation, inspect its first run before claiming clean-runner validation. Emulator and physical-device tests remain separate.
