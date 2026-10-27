# Trade screen money icon regression

Run on Paper 26.2 with Vault and an economy provider. Test with a non-op
customer and an NPC containing two BUY offers at different prices, a SELL
offer, and an item TRADE offer. Keep ordinary gold nuggets in the customer's
inventory to verify they are preserved. Repeat with a player shop and linked
storage as well as an admin shop.

| Action | Expected result |
| --- | --- |
| Alternate BUY 1 / BUY 2 rapidly, including 1 / 2 / 1 within one tick | Only the latest selection receives its money icon; no named money items appear in player storage or on the cursor. |
| Alternate BUY / SELL and BUY / TRADE | Previous money icon disappears; the player's inventory is synchronized even when the destination is not a BUY offer. Real payment items are returned normally. |
| Close immediately after selecting BUY, then reopen another shop | Pending work from the previous session cannot insert an icon into either inventory. |
| Click, shift-click, double-click, number-key swap, offhand swap, drag over, or press Q on a money icon | No money icon can be transferred, cloned, or dropped. The client returns to the server's inventory contents. |
| Repeat switching with a full inventory and with an item on the cursor | No money icons become world drops; real items and the cursor are preserved. |
| Buy normally and with shift-click | Correct items and stock change; Vault charges the configured price for each completed transaction. |
| Sell normally and with shift-click | Correct real items are removed and Vault credits money; the result icon never becomes an item. |
| Perform an item trade, including a trade using ordinary gold nuggets | Only the configured real items are exchanged; untagged gold nuggets are not deleted. |
| Close, disconnect/rejoin, and stop the server while a BUY offer is selected | Money icons are removed; no shutdown scheduler errors occur. |

For an existing escaped icon bearing the `atradenpc:display` tag, verify that
clicking or dropping it is blocked and cleans the player's inventory. Attempt
to pick up an escaped icon on the ground: the pickup is cancelled and that
item entity is removed. Other ordinary dropped items must remain unaffected.

The build checks API compatibility. These client prediction and packet timing
checks require a running server and Minecraft client; a successful build alone
does not establish that the in-game reproduction passes.
