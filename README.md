# Level Up Stats (NeoForge 1.21.1)

Spend your normal XP levels on character stats. Press **K** (rebindable under Controls > Level Up Stats) to open the menu.
Hover a stat row to see everything it gives. **Effect Sliders** turns each stat's effects down (0-100%) without losing the points.

## Stats
| Stat | What it does (per level unless noted) |
|---|---|
| Strength | +0.5 melee damage, +0.1 knockback, bonus reach for hitting mobs and placing/breaking blocks (grows with the square of your level, max +3 blocks) |
| Dexterity | Speed, jump height and attack speed, all growing faster at higher levels; softer landings |
| Constitution | +1 max heart, plus extra regeneration (0.03 HP/sec per level) |
| Intelligence | +1 Fortune and Looting per 2 levels (no tool needed, stacks with enchanted gear), +0.5 Luck, +10% crop yield (x2 at level 10) |
| Wisdom | XP orbs worth +0.5x each level, +8% chance of twin babies per level, +1 extra copy of furnace output every 5 levels |
| Charisma | 4% villager discount per level, +1 extra copy of trade results every 5 levels |

## Cost and cap
- The Nth point in a stat costs N XP levels.
- Every stat starts capped at level 10. When all six are at the cap, **Prestige** unlocks (button on the *Prestige Perks* tab).
- Prestiging resets every stat to level 1, raises the cap by 5, gives perk points (1 on the 1st prestige, 2 on the 2nd, 3 on the 3rd...) and a permanent XP multiplier (2x, 4x, 8x...). The multiplier stacks with Wisdom.

## Prestige Perks (K menu > Prestige Perks tab)
Unlock with perk points, then toggle ON/OFF. Cost in brackets.
- (1) **Water Walker**, **Double Jump**, **Ore Glow** (right-click a stone/deepslate wall with a pickaxe), **Lightning Caller** (sneak + axe hit, hits pets, 20 s cooldown)
- (2) **Vein Miner** and **Tree Feller** (separate; sneak while mining; up to 64 blocks), **Lava Swimmer**, **Mark TP**, **Heartbeat**, **Stasis Field**, **Decoy Clone**, **Earthshaker**, **Second Wind**
- (3) **Peacekeeper**, **Egg Conjurer** (needs 'Free the End', one egg per 2 days, no bosses), **Pocket Dimension** (one mob under 80% of your max health, right-click to store, place to release)

Keys (changeable in Controls): K menu, V place marker (sneak+V removes), B teleport to marker, Z Stasis Field, X Decoy Clone.

## Upgrading from 1.0.0
The old stats are reset and every XP level you spent is refunded the next time you log in.

## Notes
- **Furnaces:** the bonus applies to a furnace you have opened (right-clicked) since the server started, as long as you are online. The last player to open it gets the credit. Hoppers feeding it still work.
- **Crops:** applies when you break a fully grown crop. Right-click harvesting (e.g. some Farmer's Delight crops) is not boosted yet.
- **Villager discount:** applies to villagers, not wandering traders.
- **Looting with an empty hand** uses a simplified bonus (extra count on stackable drops). With an item in hand it uses the real Looting path.

## Building
Easiest: push this folder to GitHub; the included workflow builds the jar (Actions tab > latest run > Artifacts).
Or locally: JDK 21, add a Gradle wrapper, run `./gradlew build`; the jar is in `build/libs/`.

## Tuning
All balance numbers are constants in `src/main/java/com/levelupstats/StatEffects.java`; cap values are in `PlayerStats.java`.
