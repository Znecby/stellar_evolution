# Contributing

Thanks for your interest in Stellar Evolution.

## Before you start

- This is an alpha mod; APIs, IDs, and balance values may change.
- For bug reports, attach `latest.log` or a crash report, your mod list, and reproduction steps. Reports without logs are usually not actionable.
- For larger changes, open an issue first to discuss the design.

## Development setup

Requirements: Minecraft 26.1.2, NeoForge 26.1.2.114, Java 25.

The build uses compile-only API jars for the optional integrations. Create `run/mods/` and place these files there:

| Mod | File |
|---|---|
| JEI | `jei-26.1.2-neoforge-29.43.0.107.jar` |
| Jade | `jade-26.1.8-neoforge.jar` |
| GuideME | `guideme-26.1.17-beta.jar` |

Then run:

```bash
./gradlew build
./gradlew runUnitTests
./gradlew runClient
```

On Windows, use `gradlew.bat` instead.

## Pull requests

- Keep changes focused; avoid unrelated refactors.
- Run `./gradlew build runUnitTests` before submitting.
- Update the local documentation or guidebook when behavior or balance changes.
- Do not commit generated files, local screenshots, or IDE settings.

## License

By contributing, you agree that your contributions are licensed under the MIT License.
