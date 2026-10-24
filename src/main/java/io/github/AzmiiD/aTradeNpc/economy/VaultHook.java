package io.github.AzmiiD.aTradeNpc.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Vault economy. The service is looked up lazily, because the economy plugin
 * (EssentialsX, CMI, ...) may register itself after ATradeNPC has enabled.
 */
public class VaultHook {

    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private Economy economy;
    private String currencyFormat = "$%amount%";

    public void setCurrencyFormat(String currencyFormat) {
        this.currencyFormat = currencyFormat;
    }

    private Economy economy() {
        if (economy == null && Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        }
        return economy;
    }

    public boolean isAvailable() {
        return economy() != null;
    }

    public boolean has(OfflinePlayer player, double amount) {
        Economy eco = economy();
        return eco != null && Double.isFinite(amount) && (amount <= 0 || eco.has(player, amount));
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        Economy eco = economy();
        if (eco == null || !Double.isFinite(amount)) {
            return false;
        }
        return amount <= 0 || eco.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        Economy eco = economy();
        if (eco == null || !Double.isFinite(amount)) {
            return false;
        }
        return amount <= 0 || eco.depositPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        synchronized (FORMAT) {
            return currencyFormat.replace("%amount%", FORMAT.format(amount));
        }
    }
}
