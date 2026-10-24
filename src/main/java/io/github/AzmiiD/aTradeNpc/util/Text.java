package io.github.AzmiiD.aTradeNpc.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Text parsing. Every configurable string supports MiniMessage tags mixed with
 * legacy codes, e.g. {@code "&#ffffff&lShop &7>> <gradient:red:blue>test</gradient>"}.
 * Legacy codes are converted to MiniMessage tags first, then everything is parsed by MiniMessage.
 */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String COLOR_CODES = "0123456789abcdef";

    private Text() {
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return MM.deserialize(legacyToMini(input));
    }

    /** Parses a string for item names/lore: no default italic. */
    public static Component item(String input) {
        return parse(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> itemLore(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(item(line));
        }
        return out;
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Serializes a component back to a MiniMessage string, so it can be embedded in another message. */
    public static String mini(Component component) {
        return MM.serialize(component);
    }

    /** Replaces literal placeholders inside an item's name and lore (keeps formatting). */
    public static ItemStack replaceInItem(ItemStack stack, Map<String, String> placeholders) {
        if (stack == null || placeholders.isEmpty()) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (meta.hasDisplayName()) {
            meta.displayName(replace(meta.displayName(), placeholders));
        }
        List<Component> lore = meta.lore();
        if (lore != null) {
            List<Component> out = new ArrayList<>(lore.size());
            for (Component line : lore) {
                out.add(replace(line, placeholders));
            }
            meta.lore(out);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static Component replace(Component component, Map<String, String> placeholders) {
        Component out = component;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String value = e.getValue() == null ? "" : e.getValue();
            out = out.replaceText(TextReplacementConfig.builder()
                    .matchLiteral(e.getKey())
                    .replacement(builder -> parse(value))
                    .build());
        }
        return out;
    }

    public static String replace(String input, Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }
        String out = input;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    /**
     * Converts legacy {@code &} codes ({@code &a}, {@code &l}, {@code &#rrggbb}, {@code &x&r&r&g&g&b&b})
     * into MiniMessage tags. Like legacy chat, a color code turns off formatting started by legacy codes.
     */
    static String legacyToMini(String in) {
        if (in.indexOf('&') < 0 && in.indexOf('§') < 0) {
            return in;
        }
        StringBuilder sb = new StringBuilder(in.length() + 16);
        Set<String> openDecorations = new LinkedHashSet<>();
        int len = in.length();
        for (int i = 0; i < len; i++) {
            char c = in.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < len) {
                char next = Character.toLowerCase(in.charAt(i + 1));
                // &#rrggbb
                if (next == '#' && isHex(in, i + 2, 6)) {
                    closeDecorations(sb, openDecorations);
                    sb.append("<#").append(in, i + 2, i + 8).append('>');
                    i += 7;
                    continue;
                }
                // &x&r&r&g&g&b&b (bungee hex)
                if (next == 'x' && isBungeeHex(in, i + 2)) {
                    closeDecorations(sb, openDecorations);
                    sb.append("<#");
                    for (int k = 0; k < 6; k++) {
                        sb.append(in.charAt(i + 3 + k * 2));
                    }
                    sb.append('>');
                    i += 13;
                    continue;
                }
                if (COLOR_CODES.indexOf(next) >= 0) {
                    closeDecorations(sb, openDecorations);
                    sb.append('<').append(colorName(next)).append('>');
                    i++;
                    continue;
                }
                String deco = decorationTag(next);
                if (deco != null) {
                    sb.append('<').append(deco).append('>');
                    openDecorations.add(deco);
                    i++;
                    continue;
                }
                if (next == 'r') {
                    sb.append("<reset>");
                    openDecorations.clear();
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static void closeDecorations(StringBuilder sb, Set<String> open) {
        for (String deco : open) {
            sb.append("<!").append(deco).append('>');
        }
        open.clear();
    }

    private static boolean isHex(String s, int start, int count) {
        if (start + count > s.length()) {
            return false;
        }
        for (int i = start; i < start + count; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBungeeHex(String s, int start) {
        if (start + 12 > s.length()) {
            return false;
        }
        for (int k = 0; k < 6; k++) {
            char amp = s.charAt(start + k * 2);
            if (amp != '&' && amp != '§') {
                return false;
            }
            if (Character.digit(s.charAt(start + k * 2 + 1), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String decorationTag(char c) {
        return switch (c) {
            case 'l' -> "bold";
            case 'o' -> "italic";
            case 'n' -> "underlined";
            case 'm' -> "strikethrough";
            case 'k' -> "obfuscated";
            default -> null;
        };
    }

    private static String colorName(char c) {
        return switch (c) {
            case '0' -> "black";
            case '1' -> "dark_blue";
            case '2' -> "dark_green";
            case '3' -> "dark_aqua";
            case '4' -> "dark_red";
            case '5' -> "dark_purple";
            case '6' -> "gold";
            case '7' -> "gray";
            case '8' -> "dark_gray";
            case '9' -> "blue";
            case 'a' -> "green";
            case 'b' -> "aqua";
            case 'c' -> "red";
            case 'd' -> "light_purple";
            case 'e' -> "yellow";
            default -> "white";
        };
    }
}
