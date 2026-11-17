# PVO — player-guided fireworks

Server-side Fabric mod for Minecraft **26.2**. Firework rockets shot from a crossbow lock onto an airborne
target in front of the shooter and home in on it. Vanilla clients need nothing installed.

## How it works

When a firework rocket is launched, the server looks for a target inside a cone in front of the shooter.
Found one — the rocket steers towards it every tick until it hits, loses the target or runs out of lifetime.

Tuned for flying mobs: vexes, ghasts, happy ghasts, wither, ender dragon and other airborne entities.
Bats are excluded. Ground mobs are not targeted.

## Tuning

Everything lives in `com.example.PvoConfig` as constants (no JSON config):

| constant | default | what |
|---|---|---|
| `TARGET_RANGE` | `128.0` | search radius |
| `CONE_ANGLE_DEGREES` | `30.0` | half-angle of the search cone |
| `SEARCH_INTERVAL_TICKS` | `3` | re-target interval |
| `ARM_DELAY_TICKS` | `3` | ticks before guidance starts |
| `PROXIMITY_RADIUS` | `1.0` | detonation radius |
| `INITIAL_SPEED` / `MAX_SPEED` | `0.5` / `4.5` | blocks per tick |
| `THRUST_DURATION_TICKS` | `35` | powered flight time |
| `MAX_TURN_DEGREES_PER_TICK` | `1.2` | steering rate |
| `LOST_TARGET_MEMORY_TICKS` | `30` | how long a lost target is remembered |
| `MAX_LEAD_TICKS` | `10.0` | target leading |
| `MAX_LIFETIME_TICKS` | `120` | hard lifetime cap |
| `DEBUG` | `false` | log targeting decisions |

## Install

Fabric Loader ≥ 0.20.1 + Fabric API on the server, jar into `mods/`.

## Build

```
./gradlew build
```

## License

MIT.
