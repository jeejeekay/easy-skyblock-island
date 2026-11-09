# Easy Skyblock Island

A small Forge 1.20.1 mod that turns any dimension into a superflat-style void or ocean world and drops an island you built into it. It has one island per dimension, with no teams, invites, visiting or protection.

- **Generator:** each dimension gets a list of solid layers (or none) plus an optional liquid up to a sea level. With no layers and no liquid, it's a plain void.
- **Every dimension:** the overworld, Nether, End and any modded dimension can each be switched on or off. Modded dimensions are added to the config automatically, switched off.
- **Biomes:** keep the normal biomes, or give a whitelist or blacklist.
- **Islands you build:**
  1. Build the island.
  2. Place an **Island Spawn Marker** where players should stand, with the arrow facing where they should look.
  3. Mark two corners with the **Island Wand** (use **Island Boundary Markers** to mark corners in mid-air).
  4. Run `/esi save <name>`.

  Chests are saved with their contents, so a chest on the island is the starting kit.
- **Placement:** an island goes either at spawn when the world is created, or at the spot where the first player arrives in that dimension (Nether portal, End platform, a rocket landing, and so on).
- **World type:** "Skyblock Islands" is added to the Create World screen and selected by default (this can be turned off). Its **Customize** button lets you pick which saved island each dimension gets.

## Building

Gradle has to run on a **JDK 17** (Gradle 8.8 can't run on Java 23 or newer). On Windows PowerShell, point `JAVA_HOME` at a JDK 17 for the session and use `.\gradlew` (plain `gradlew` runs whatever is on your PATH, not this project's wrapper):

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot"   # your JDK 17 folder
.\gradlew build
```

On Linux/macOS: `./gradlew build`. The jar ends up in `build/libs/`. The mod uses a mixin and access transformers, and both are set up in `build.gradle`.

## Using it

1. Create a world with the **Skyblock Islands** world type. Use **Customize** to change the islands if you like.
2. On a dedicated server, set `level-type=easy_skyblock_island\:islands` in `server.properties` before the world is generated.

Only worlds created with this world type are affected. Each world copies the settings into `<world>/easy_skyblock_island.json` the moment it's created, and the islands it uses into `<world>/easy_skyblock_island_islands/`. Editing the global config afterwards doesn't reshape existing worlds; to change a world, edit its own copy and restart it. Generator changes only affect chunks that haven't been generated yet.

### Commands

| Command | Permission | What it does |
|---|---|---|
| `/esi save <name>` | op | Saves the Island Wand selection as `config/easy_skyblock_island/islands/<name>.nbt`. Air isn't saved, so a placed island never carves a hole in water or terrain. |
| `/esi list` | anyone | Lists saved islands. |
| `/esi place <name> [pos]` | op | Places an island with its spawn marker at the position, for testing. |
| `/esi status` | anyone | Shows the generator and island for the current dimension. |

### Island Wand

- Use it on a block to set corner 1.
- Sneak and use it on a block to set corner 2.
- Sneak and use it in the air to clear both corners.

While you hold the wand, the selection is drawn as an outline.

### Island Boundary Marker

- A block for marking corners out in the void. Use it while not looking at a block and it's placed two blocks in front of you, in mid-air.
- Click it with the Island Wand like any other block. It is never saved into an island.

Air of any kind (including cave air) and structure voids aren't saved either, so a placed island only replaces blocks where it actually has blocks; water or terrain around and inside it stays.

### Island Spawn Marker

- Players appear standing on the marker, facing its arrow.
- The marker is removed when the island is placed.
- If an island has no marker, players appear on top of its centre.

When an island is placed, it never overwrites solid blocks that are already there, such as a Nether portal frame or the End platform. Air, water and other replaceable blocks are overwritten.

## Configuration

The config file is `config/easy_skyblock_island/config.json`. The defaults are:

- **Overworld:** the ocean world (layers plus water up to y 62) with the `default` island at spawn.
- **Nether:** a void with the `nether` island at the first portal. That island is crimson nylium on one half and warped nylium on the other, with both fungi, soul sand with nether wart, and a netherrack underside.
- **End:** vanilla, but set up so switching it on gives a void End that still has its pillars and return gateway.

```jsonc
{
  "make_default_world_type": true,
  "dimensions": {
    "minecraft:overworld": {
      "enabled": true,              // false = this mod leaves the dimension alone
      "replace_generator": true,    // false = keep normal terrain (the biome list still applies)
      "generator": {
        "layers": "minecraft:bedrock,31*minecraft:deepslate,32*minecraft:stone,3*minecraft:sand", // bottom first; "" = no floor
        "layers_bottom_y": null,    // null = bottom of the dimension
        "liquid": "minecraft:water",// "" = no sea
        "liquid_bottom_y": null,    // null = bottom of the dimension
        "sea_level": 63             // liquid fills up to sea_level - 1; null = the dimension's normal sea level
      },
      "biomes": {
        "use_list": false,          // false = normal biomes
        "list_is_whitelist": true,  // true = only these, false = all except these
        "list": ["minecraft:plains", "#minecraft:is_forest", "biomesoplenty:*"],
        "checkerboard_size": 3      // patch size when listed biomes aren't native to the dimension
      },
      "features": {                 // trees, ores, flowers, lakes, ...
        "generate": false,
        "list_is_whitelist": false,
        "list": []
      },
      "structures": {               // villages, fortresses, ...
        "generate": false,
        "list_is_whitelist": false,
        "list": []                  // structure ids, structure set ids, "#tags" or "modid:*"
      },
      "island": {
        "template": "default",      // file name in config/easy_skyblock_island/islands, "" = none
        "placement": "spawn",       // "spawn" or "first_arrival"
        "y": null,                  // y of the spawn marker; null = on the surface (spawn) / the arrival point (first_arrival)
        "x": 0,                     // spawn placement only
        "z": 0
      }
    }
  }
}
```

### Notes

- **Biomes:** a whitelist of biomes that are native to the dimension keeps the vanilla climate layout, so the listed biomes spread into the gaps. Biomes from elsewhere fall back to a checkerboard. Mixing biomes from different dimensions can upset feature ordering, so prefer native biomes when features are on.
- **A sea with no floor works**, because liquid placed during generation doesn't flow. If you break a block at the bottom edge of the sea, though, water will start falling into the void below. Add a floor layer (`"minecraft:barrier"` or `"minecraft:bedrock"`) if that matters.
- **Placement:**
  - With `spawn`, the island is placed at x/z when the world is created. New players start on the overworld's spawn island if it has one, otherwise on the first dimension that does.
  - Players who die without a bed or anchor go back to that island.
  - With `first_arrival`, the island is placed under the first player to enter the dimension. Set `y` for dimensions where you arrive mid-air, such as Ad Astra planets.
- **The End:** if you switch the End on with `features` off, leave `minecraft:end_spike` and `minecraft:end_gateway_return` in the whitelist so the dragon fight still works.

## License

MIT
