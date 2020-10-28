# Codegen Integration Test
This module defines an integration test of the code generation machinery. Models defined in `model` are built and generated into a Rust package. A `cargoCheck` Gradle task ensures that the generated Rust code compiles. This is added as a finalizer of the `test` task. This will currently generate clients for Ebs & DynamoDB. Note that these are "vanilla" clients that won't have any customizations or other AWS features enabled.

A fake Cargo workspace, `Cargo.toml.tmpl` specifies where to find the build artifacts for Cargo. Gradle copies it into place before running tests.

## Usage
These tests can only be run from the repo root.
```
# Compile codegen, Regenerate Rust, compile:
REPO_ROOT=$PWD ./gradlew :codegen-test:test
```

The `smithy-build.json` configures the runtime dependencies to point directly to `../rust-runtime/*` via relative paths.