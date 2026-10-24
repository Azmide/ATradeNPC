package io.github.AzmiiD.aTradeNpc.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Base64;
import java.util.Locale;

/**
 * Item helpers. Transactions work on cloned {@code ItemStack[]} arrays first ("simulation"),
 * and are only written back to the real inventory when every step succeeded.
 */
public final class ItemUtil {

    private ItemUtil() {
    }

    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    /** Full binary item data (keeps every component: custom names, enchants, custom model data, plugin data...). */
    public static String encode(ItemStack stack) {
        return isEmpty(stack) ? null : Base64.getEncoder().encodeToString(stack.serializeAsBytes());
    }

    public static ItemStack decode(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
    }

    /** Name used inside chat messages/lore, as a MiniMessage string. */
    public static String displayName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return Text.mini(meta.displayName());
        }
        if (meta != null && meta.hasItemName()) {
            return Text.mini(meta.itemName());
        }
        return prettyMaterial(stack.getType());
    }

    public static String prettyMaterial(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** Deep copy, so changes never leak into the real inventory (Bukkit may return mirrors). */
    public static ItemStack[] copy(ItemStack[] contents) {
        ItemStack[] out = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            out[i] = isEmpty(contents[i]) ? null : contents[i].clone();
        }
        return out;
    }

    public static int count(ItemStack[] contents, ItemStack template) {
        int total = 0;
        for (ItemStack stack : contents) {
            if (!isEmpty(stack) && stack.isSimilar(template)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Removes {@code amount} items similar to {@code template}. Returns false (contents partly changed) if not enough. */
    public static boolean remove(ItemStack[] contents, ItemStack template, int amount) {
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (isEmpty(stack) || !stack.isSimilar(template)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            left -= take;
            if (take == stack.getAmount()) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
        return left <= 0;
    }

    /** Adds {@code amount} items like {@code template}, respecting max stack sizes. Returns how many did NOT fit. */
    public static int add(ItemStack[] contents, ItemStack template, int amount) {
        return add(contents, 0, template, amount);
    }

    /** Like {@link #add(ItemStack[], ItemStack, int)}, but only uses slots from index {@code start}. */
    public static int add(ItemStack[] contents, int start, ItemStack template, int amount) {
        int max = Math.max(1, template.getMaxStackSize());
        int left = amount;
        // First top up existing stacks.
        for (int i = start; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (!isEmpty(stack) && stack.isSimilar(template) && stack.getAmount() < max) {
                int put = Math.min(left, max - stack.getAmount());
                stack.setAmount(stack.getAmount() + put);
                left -= put;
            }
        }
        // Then empty slots.
        for (int i = start; i < contents.length && left > 0; i++) {
            if (isEmpty(contents[i])) {
                int put = Math.min(left, max);
                ItemStack stack = template.clone();
                stack.setAmount(put);
                contents[i] = stack;
                left -= put;
            }
        }
        return left;
    }
}
