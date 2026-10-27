# ATradeNPC

Shopkeeper NPCs for **Paper 26.2** that trade **any item**, including custom items, and buy or sell items for **Vault money**.
Inspired by [Shopkeepers](https://github.com/Shopkeepers/Shopkeepers), which only supports item trades.

Customers use the **vanilla villager trading screen**. Owners edit their shop in a GUI.

## Features

- **3 offer types per NPC**
  - **Trade**: 1–2 items for an item. Any item works (names, lore, enchants, custom model data, plugin items), and items are matched exactly.
  - **Buy**: the customer pays money (Vault) and receives an item.
  - **Sell**: the customer gives an item and receives money (Vault).
- **Admin shops** with infinite stock.
- **Player shops** with stock from a linked chest or barrel.
  - Money from sales goes to the owner. Money for Sell offers is paid by the owner.
  - The storage chest is protected from other players, hoppers and explosions.
  - Optional sales tax and a per-player shop limit (`atradenpc.limit.<n>`).
- **Vanilla villager trading screen** for customers. Money is shown as a coin item, and out-of-stock offers are crossed out.
- **In-game editor** (sneak + right-click the NPC):
  - Add, edit, duplicate and reorder offers. Set prices in chat.
  - Rename the shop, change the NPC type (villager, zombie, piglin, …) and the villager profession.
  - Move or center the NPC, open the storage chest, delete the shop.
- **NPCs look at nearby players** and stand in the **center of their block**.
- **Optional FancyHolograms integration**: link an existing hologram to a shop, edit it with FancyHolograms commands, and keep its position relative to the NPC when moving the shop.
- NPCs can't be hurt, pushed, leashed, converted or set on fire.
- Every transaction is simulated before it is applied, so items are never duplicated or lost.
- Configurable sounds, transaction log, and messages with MiniMessage, `&` codes and `&#rrggbb` hex colors.

## Requirements

- Paper **26.2**
- Java **25**
- [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin (EssentialsX, CMI, …). These are optional: without them, Trade offers still work.

## Commands

| Command | Description |
|---|---|
| `/atrade create admin [type] [name]` | Admin shop (infinite stock) at your location |
| `/atrade create player [type] [name]` | Player shop. Look at the storage chest/barrel |
| `/atrade edit [id]` | Open the editor (or sneak + right-click the NPC) |
| `/atrade remove [id]` | Remove a shop |
| `/atrade rename <id> <name>` | Rename a shop |
| `/atrade movehere <id>` | Move the NPC to you |
| `/atrade center [id]` | Center the NPC on its block |
| `/atrade hologram link <id> <hologram>` | Link an existing FancyHolograms hologram |
| `/atrade hologram unlink <id>` | Unlink and keep the hologram |
| `/atrade hologram info <id>` | Show the linked hologram and availability |
| `/atrade list [player\|admin\|all]` | List shops |
| `/atrade tp <id>` | Teleport to a shop |
| `/atrade open <id> [player]` | Open a shop's trade screen (console / other plugins) |
| `/atrade reload` | Reload config and messages |

Aliases: `/atradenpc`, `/tradenpc`. Without an id, commands use the NPC you are looking at.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `atradenpc.use` | everyone | Trade with shop NPCs |
| `atradenpc.create.player` | everyone | Create player shops |
| `atradenpc.create.admin` | op | Create and edit admin shops |
| `atradenpc.open` | op | `/atrade open` |
| `atradenpc.hologram` | op | Manage hologram links (also requires edit access to the shop) |
| `atradenpc.limit.<n>` | – | Max number of player shops |
| `atradenpc.limit.bypass` | op | No shop limit |
| `atradenpc.admin` | op | Edit/remove every shop, bypass chest protection, reload |

## Building

```bash
mvn clean package
```

The jar is written to `target/ATradeNPC-<version>.jar`. Every push to GitHub also builds the jar automatically (see the **Actions** tab).

## FancyHolograms

Install a FancyHolograms **2.x** release compatible with your Paper server (integration built against API **2.9.1**).
FancyHolograms is optional; shops and trades work without it. FancyNpcs is not required.

Create a hologram using FancyHolograms, then link it by name:

```text
/hologram create text toko_pedang
/hologram edit toko_pedang setline 1 <gold>Toko Pedang
/hologram edit toko_pedang addline <gray>Klik kanan untuk belanja
/atrade hologram link 1 toko_pedang
/atrade hologram info 1
```

- Linking places the hologram directly above the NPC (`holograms.link-height`, default 2.5 blocks above its feet). Run the same link command again to recenter an old or misplaced link; unlinking first is not required.
- Continue editing text, colors, background, scale, visibility and position through `/hologram edit <name> ...`.
- `/atrade movehere` and `/atrade center` move the linked hologram with the NPC, preserving its relative position and orientation. Manual hologram position edits become the new offset on the next NPC move.
- Links and offsets are saved in `data/shops.yml`. If FancyHolograms or the named hologram is temporarily unavailable during an NPC move, the pending position is applied when it becomes available again.
- One hologram can be linked to one shop. It must be persistent, in the same world when linking, and not linked to FancyNpcs.
- NPC entities have no vanilla custom name, so no extra label appears when targeting them. Display names above NPCs are managed only by FancyHolograms. Shop names are still stored and used in menus/commands. The old `holograms.hide-npc-name` setting is ignored, including existing `false` values.
- Unlinking or deleting an ATradeNPC shop **keeps** the hologram. Remove it separately with FancyHolograms if desired. To rename a hologram, unlink it and link its new name.
- Alias: `/atrade holo`. Linking requires `atradenpc.hologram` (op by default) or `atradenpc.admin`, plus permission to edit that shop.

FancyHolograms' own `linkwithnpc` command is for FancyNpcs; use `/atrade hologram link` for ATradeNPC.

## Files

- `config.yml`: NPC behaviour, entity types, economy, player shops, sounds
- `lang.yml`: every message and GUI text
- `data/shops.yml`: saved shops (items are stored as full binary item data)
- `data/transactions.log`: one line per transaction

### Updating

Just replace the jar. On startup (and on `/atrade reload`) new options from the updated `config.yml` and `lang.yml`
are added to your existing files with their default values and comments. Your own values are never changed.
Before a file is changed, a copy is saved in `plugins/ATradeNPC/backups/`.

### 1.3.2

- Remove NPC entity custom names completely, including hover/target labels. FancyHolograms is the only source of floating labels.
- Retire `holograms.hide-npc-name`; existing copies of that setting are ignored. Shops can still run without FancyHolograms, with no floating name label.
- Keep `holograms.link-height` for adjusting placement; changing it takes effect on the next link/relink.

### 1.3.1

- Linking/relinking immediately places the hologram above its shop NPC instead of preserving an arbitrary distant offset.
- Add `holograms.link-height` (default 2.5) through the automatic config updater.
- Explicitly refresh current FancyHolograms viewers after moving so they receive the new position immediately.
- Do not report a successful link or replace the previous link if the provider cannot move/save the hologram.

### 1.3.0

- Add optional FancyHolograms linking, unlinking, info and tab completion.
- Preserve external hologram appearance edits and follow shop moves, including deferred moves while the provider is unavailable.
- Save links across restarts and hide the ordinary NPC name tag while a linked hologram is available.
- Add `holograms.hide-npc-name` and integration messages through the existing automatic config/language updater.
- Fix a missing YAML separator in the entity-type error message, including automatic repair of old language files with a backup.

### 1.2.1

- Fix money display icons appearing in the player's inventory when switching offers rapidly.
- Synchronize inventory after every offer selection and discard pending updates from older selections.
- Block moving, dropping, or picking up escaped money display icons.
- Existing config and language files are updated automatically as described above. This patch adds no new settings.
