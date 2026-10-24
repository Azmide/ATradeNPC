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
| `atradenpc.limit.<n>` | – | Max number of player shops |
| `atradenpc.limit.bypass` | op | No shop limit |
| `atradenpc.admin` | op | Edit/remove every shop, bypass chest protection, reload |

## Building

```bash
mvn clean package
```

The jar is written to `target/ATradeNPC-<version>.jar`. Every push to GitHub also builds the jar automatically (see the **Actions** tab).

## Files

- `config.yml`: NPC behaviour, entity types, economy, player shops, sounds
- `lang.yml`: every message and GUI text
- `data/shops.yml`: saved shops (items are stored as full binary item data)
- `data/transactions.log`: one line per transaction
