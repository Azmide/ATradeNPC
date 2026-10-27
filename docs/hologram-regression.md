# FancyHolograms integration checks

Automated tests cover link serialization and old shop compatibility, duplicate
links, FancyNpcs/persistence conflicts, missing providers, unlinking without
deleting the hologram, manual position edits, NPC moves across worlds, deferred
moves across restarts, provider reload fallback, valid resource YAML, and config
upgrade backups/preservation of existing settings.

On a Paper 26.2 server with a compatible FancyHolograms 2.x plugin:

1. Start once without FancyHolograms. Existing shops and trading should work;
   linking should return a clear provider-unavailable message without a stack trace.
2. Install FancyHolograms, restart, create a text hologram above a shop, and run
   `/atrade hologram link <shop-id> <name>`. Verify `/atrade hologram info` and tab completion.
3. Edit text, colors, background, scale, visibility and position with
   `/hologram edit`. ATradeNPC must not reset these edits during periodic checks.
4. Move and center the NPC using both commands and the editor. Check position
   offsets, orientation, world changes, persistence after restart, and no duplicate holograms.
5. Verify the ordinary NPC name tag hides only while the named hologram is
   available. Remove/reload the hologram, then recreate it with the same name;
   the fallback name tag should update within two seconds.
6. Confirm `holograms.hide-npc-name: false` survives a config upgrade and reload.
   Newly added messages/options should appear without replacing existing values.
7. Unlink and delete shops: the externally managed hologram must remain.
8. A non-op without `atradenpc.hologram` cannot link. A user granted that
   permission still cannot edit another owner's shop. Console/admin can link.
9. Attempt to reuse a hologram for a second shop, use a nonpersistent hologram,
   or use a FancyNpcs-linked hologram. Each should be rejected.

Automated checks do not replace observing hologram packets/rendering with a
Minecraft client. The optional bridge uses the published FancyHolograms 2.9.1 API;
different major APIs require a separate bridge.
