package io.github.AzmiiD.aTradeNpc.hologram;

import org.bukkit.Location;

import java.util.List;

/** Keeps optional provider classes out of the shop and command code. */
public interface HologramBridge {
    record Entry(String name, Location location, boolean persistent, boolean linkedToFancyNpc) {
    }

    Entry find(String name);

    List<String> names();

    boolean move(String name, Location location);
}
