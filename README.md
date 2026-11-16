<p align="center">
  <img src="src/main/resources/icon.png" width="128" alt="Stellar Evolution icon">
</p>

# Stellar Evolution

An industrial technology mod for Minecraft, built on NeoForge.

Stellar Evolution is a work-in-progress tech mod centered on a **thermal pressure network**, large multiblock machines, and multi-stage production chains. It takes inspiration from modern industrial mods while using its own energy and logistics model: generators provide differential pressure capacity and heat flow, and consumers compete for that capacity across a single network.

> [!WARNING]
> **AI-assisted development.** This project is developed with extensive use of AI-assisted tools. Code, documentation, balance values, and assets may be partially or fully AI-generated and should be reviewed before being relied upon.
>
> **Bug reports require logs.** When opening an issue, always attach the full `latest.log` or crash report, your mod list, and clear reproduction steps. Reports without logs are usually impossible to analyze — including with AI assistance.

![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-green)
![NeoForge](https://img.shields.io/badge/NeoForge-26.1.2.114-orange)
![Java](https://img.shields.io/badge/Java-25-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

## Project Status

- **Version:** 0.1.0
- **Minecraft:** 26.1.2
- **Loader:** NeoForge 26.1.2.114
- **Java:** 25
- **State:** Alpha

> The mod is under active development. Content, balance, and internal IDs may change between builds. Legacy world compatibility is **not** provided.

## Features

### Thermal Pressure Network

- Generators add **differential pressure (DP) capacity** and provide a **heat flow rate (HP/t)**.
- Capacity is summed across the network; heat flow uses the highest generator value.
- Consumers reserve capacity based on thermal resistance and available heat flow.
- Overloading stops every consumer on the network.
- Machine speed scales with heat flow relative to each machine's base flow rate.
- Copper pipes, flow limiters, and manually cut pipe connections are supported.

### Multiblock Machines

- 2×2×2 large machines with animated moving parts and a shared machine framework.
- 3×3×4 coke oven with controller-driven structure validation and hatch casing updates.
- 15×19×15 mass driver with cross-dimensional launch capability.
- Fixed multiblock patterns with an in-game building tool.

### Production Chains

- **Bio-processing:** slurry mixing, fermentation, decomposition, sterilization, crystallization, and bioreaction.
- **Petrochemicals:** distillation, cracking, reforming, polymerization, gasification, and synthesis.
- **Coal and wood chemistry:** coke oven, coal liquefaction, wood dry distillation, and derived chemicals.
- **Cryogenic air separation:** liquid air, nitrogen, oxygen, argon, and helium.
- **Nuclear fission:** fuel processing, enrichment, fuel rod assembly, fission reactors, spent fuel pools, and steam turbine units.
- **Muon-catalyzed fusion:** muon-driven fusion core with cooling-dependent stability.
- **High-energy physics:** cyclotron, particle source, irradiation chamber, collider, accelerator ring, ADS, annihilation reactor, and RTG.
- **Ore processing:** crushing, leaching, purification, and titanium/rare-earth separation.
- **Bedrock resources:** multi-stage extraction of elements and actinides from bedrock.
- **Moon dimension:** low gravity, reduced fall damage, and dedicated lunar ores.
- **Equipment:** steel and tungsten-steel tools, weapons, and armor with a tool-mode system.

### Logistics and Storage

- Three tank tiers with mergeable 1×1 and 3×3 multiblock tanks.
- Mechanical pipe network for large-machine item and fluid logistics.
- Pressure pipes for steam and beam pipes for particle streams.
- Fluid-aware tank items with dynamic stack sizes.

## Requirements

| Requirement | Version |
|---|---|
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.114 |
| Java | 25 |

The mod has **no hard dependencies**. The following integrations are optional:

- **JEI** — recipe lookup
- **Jade** — block and machine information overlay
- **GuideME** — in-game guidebook

## Installation

1. Install NeoForge 26.1.2.114 for Minecraft 26.1.2.
2. Place `stellar_evolution-alpha0.1.0.jar` into the `mods` folder.
3. Optionally install JEI, Jade, and GuideME.
4. Launch the game.

## Building from Source

```bash
git clone https://github.com/Znecby/stellar_evolution.git
cd stellar_evolution
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

The built jar is written to `build/libs/stellar_evolution-alpha0.1.0.jar`.

## Development

```bash
./gradlew runClient       # Launch the development client
./gradlew runServer       # Launch a dedicated server
./gradlew runGuide        # Open the GuideME guidebook
./gradlew runUnitTests    # Run the JUnit test suite
```

> The standard Gradle `test` task is disabled in this project. Use `runUnitTests` instead.

## Documentation

- Developer documentation is maintained locally and is not included in this repository.
- The in-game GuideME guidebook source lives in [`guidebook/`](guidebook/) and is bundled with the mod.

## License

This project is licensed under the **MIT License**.
