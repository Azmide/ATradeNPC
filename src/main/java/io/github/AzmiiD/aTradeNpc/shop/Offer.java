package io.github.AzmiiD.aTradeNpc.shop;

import io.github.AzmiiD.aTradeNpc.util.ItemUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * One offer of a shop.
 * <ul>
 *     <li>TRADE: customer gives {@link #cost1} (+ {@link #cost2}) and receives {@link #item}.</li>
 *     <li>BUY: customer pays {@link #price} and receives {@link #item}.</li>
 *     <li>SELL: customer gives {@link #item} and receives {@link #price}.</li>
 * </ul>
 * Item amounts are part of the stacks. Offers are replaced (never mutated) once they are in a shop,
 * so open menus can detect changes by identity.
 */
public final class Offer {

    private OfferType type;
    private ItemStack item;
    private ItemStack cost1;
    private ItemStack cost2;
    private double price;

    public Offer(OfferType type) {
        this.type = type;
    }

    public Offer copy() {
        Offer o = new Offer(type);
        o.item = clone(item);
        o.cost1 = clone(cost1);
        o.cost2 = clone(cost2);
        o.price = price;
        return o;
    }

    private static ItemStack clone(ItemStack stack) {
        return ItemUtil.isEmpty(stack) ? null : stack.clone();
    }

    /** Null when complete, otherwise the lang key of what is missing. */
    public String validate() {
        if (ItemUtil.isEmpty(item)) {
            return type == OfferType.TRADE ? "reasonNoResult" : "reasonNoItem";
        }
        if (type == OfferType.TRADE) {
            if (ItemUtil.isEmpty(cost1) && ItemUtil.isEmpty(cost2)) {
                return "reasonNoCost";
            }
        } else if (!(price > 0) || !Double.isFinite(price)) {
            return "reasonNoPrice";
        }
        return null;
    }

    /** Moves cost2 into cost1 when only the second cost is set. */
    public void normalize() {
        if (ItemUtil.isEmpty(cost1) && !ItemUtil.isEmpty(cost2)) {
            cost1 = cost2;
            cost2 = null;
        }
        if (type != OfferType.TRADE) {
            cost1 = null;
            cost2 = null;
        } else {
            price = 0;
        }
    }

    public void save(ConfigurationSection section) {
        section.set("type", type.name());
        section.set("item", ItemUtil.encode(item));
        section.set("cost1", ItemUtil.encode(cost1));
        section.set("cost2", ItemUtil.encode(cost2));
        if (type != OfferType.TRADE) {
            section.set("price", price);
        }
    }

    public static Offer load(ConfigurationSection section) {
        Offer o = new Offer(OfferType.valueOf(section.getString("type", "TRADE")));
        o.item = ItemUtil.decode(section.getString("item"));
        o.cost1 = ItemUtil.decode(section.getString("cost1"));
        o.cost2 = ItemUtil.decode(section.getString("cost2"));
        o.price = section.getDouble("price", 0);
        return o;
    }

    public OfferType getType() {
        return type;
    }

    public void setType(OfferType type) {
        this.type = type;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = clone(item);
    }

    public ItemStack getCost1() {
        return cost1;
    }

    public void setCost1(ItemStack cost1) {
        this.cost1 = clone(cost1);
    }

    public ItemStack getCost2() {
        return cost2;
    }

    public void setCost2(ItemStack cost2) {
        this.cost2 = clone(cost2);
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }
}
