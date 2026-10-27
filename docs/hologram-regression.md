# FancyHolograms integration checks

Automated tests cover link serialization and old shop compatibility, duplicate
links, FancyNpcs/persistence conflicts, missing providers, unlinking without
deleting the hologram, manual position edits, NPC moves across worlds, deferred
moves across restarts, links surviving provider reloads, valid resource YAML, and config
upgrade backups/preservation of existing settings.

On a Paper 26.2 server with a compatible FancyHolograms 2.x plugin:

1. Start once without FancyHolograms. Existing shops and trading should work;
   linking should return a clear provider-unavailable message without a stack trace.
2. Install FancyHolograms, restart, create a text hologram above a shop, and run
   `/atrade hologram link <shop-id> <name>`. It must immediately appear directly
   above the NPC at the configured `holograms.link-height`, even if it was created
   far away. Repeat the same command to repair a distant 1.3.0 link. Verify
   `/atrade hologram info` and tab completion.
3. Edit text, colors, background, scale, visibility and position with
   `/hologram edit`. ATradeNPC must not reset these edits during periodic checks.
4. Move and center the NPC using both commands and the editor. Check position
   offsets, orientation, world changes, persistence after restart, and no duplicate holograms.
5. Aim the crosshair directly at an NPC, then look away. No vanilla name tag
   should appear, whether linked, unlinked, or missing its FancyHolograms provider.
   Repeat after renaming the shop, respawning it, reloading ATradeNPC, and restarting.
6. Leave a legacy `holograms.hide-npc-name: false` in the config. It must not bring
   back vanilla name tags. Existing settings are preserved but this old key is ignored.
   Verify `holograms.link-height` still controls placement on link/relink.
7. Unlink and delete shops: the externally managed hologram must remain.
8. A non-op without `atradenpc.hologram` cannot link. A user granted that
   permission still cannot edit another owner's shop. Console/admin can link.
9. Attempt to reuse a hologram for a second shop, use a nonpersistent hologram,
   or use a FancyNpcs-linked hologram. Each should be rejected.
10. Keep a second player watching while linking and moving the shop. Both must
    see the new position without reconnecting or reloading either plugin.

Automated checks do not replace observing hologram packets/rendering with a
Minecraft client. The optional bridge uses the published FancyHolograms 2.9.1 API;
different major APIs require a separate bridge.
