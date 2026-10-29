# Cross-platform customer trading

The AUTO route uses the Floodgate UUID lookup or a local Geyser connection lookup.
If Geyser/Floodgate runs on a proxy, Floodgate must also expose player data on the
Paper backend for automatic detection. Without backend detection, `trade-menu: chest`
selects the compatible menu for every customer. Never depend on a player's name prefix.

Tests cover AUTO/CHEST/MERCHANT routing, API UUID/connection results, providers
that are absent/not ready/incompatible, and automatic config migration. The chest
menu uses the same transaction service as Java trading, without merchant input slots.

Run these checks with a Bedrock client through the server's actual Geyser setup:

1. In AUTO mode with backend Geyser/Floodgate available, Bedrock opens a chest
   menu while Java still opens a merchant. Test both NPC right-click and `/atrade open`.
2. Tap a BUY offer with sufficient Vault money. Receive the configured real item
   and pay exactly once. Repeat with insufficient money and a full inventory:
   no item or money should be lost. No gold-nugget payment item is required.
3. Test SELL and item TRADE offers using normal and custom items. Exact item
   metadata and amounts must be checked by the transaction service.
4. Test player-shop stock, owner funds, tax, full storage and unavailable storage.
5. With more than 45 offers, navigate previous/next pages and buy from each page.
6. Try dragging, double-clicking, shift-clicking, number/offhand swaps and dropping
   menu items. Display copies must never leave the menu or become world drops.
7. Tap repeatedly, close immediately, switch to another inventory, or replace/remove
   an offer/shop before the next tick. Queued clicks must not transact after the
   screen closes or use an offer that was removed/replaced.
8. Force CHEST mode and repeat Java single and shift-click trades. Force MERCHANT
   only to opt back into the vanilla merchant, including its Bedrock limitations.
9. Test a proxy-only setup with `trade-menu: chest`; backend API detection is not
   required in this mode. Existing settings and custom language remain after upgrade.

A successful build/unit test run does not certify rendering or touch input on a
real Bedrock client. These end-to-end checks require a running Geyser server/client.
