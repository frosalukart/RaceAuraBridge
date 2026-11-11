package com.raceaurabridge;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * RaceCore mengubah BASE value MAX_HEALTH (ganti ras, siang/malam), tapi
 * AuraSkills hanya menghitung ulang HP saat join / ganti world / stat berubah.
 * Plugin ini mendeteksi perubahan base itu lalu meminta AuraSkills menghitung
 * ulang HP pemain. Tidak butuh API RaceCore sama sekali.
 */
public final class RaceAuraBridge extends JavaPlugin implements Listener {

    private final Map<UUID, Double> lastBase = new HashMap<>();

    private Object hpTrait;       // Traits.HP
    private Object hpImpl;        // handler HP milik AuraSkills
    private Method reloadMethod;  // reload(Player, Trait)
    private boolean initTried;
    private boolean warned;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        long interval = Math.max(5L, getConfig().getLong("check-interval-ticks", 20L));
        Bukkit.getScheduler().runTaskTimer(this, this::scan, 40L, interval);
        getLogger().info("RaceAuraBridge aktif (cek tiap " + interval + " tick).");
    }

    private void scan() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
            if (attr == null) continue;
            double base = attr.getBaseValue();
            Double prev = lastBase.put(p.getUniqueId(), base);
            if (prev != null && Math.abs(prev - base) > 0.001) {
                syncHealth(p);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        lastBase.remove(p.getUniqueId());
        long delay = Math.max(1L, getConfig().getLong("join-sync-delay-ticks", 60L));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) syncHealth(p);
        }, delay);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastBase.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("sync")) {
            int n = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (syncHealth(p)) n++;
            }
            sender.sendMessage("Health disinkronkan untuk " + n + " pemain.");
        } else {
            sender.sendMessage("Pakai: /raceaura sync");
        }
        return true;
    }

    /** Minta AuraSkills menghitung ulang HP, lalu rapikan darah saat ini. */
    private boolean syncHealth(Player p) {
        if (!init()) return false;
        try {
            reloadMethod.invoke(hpImpl, p, hpTrait);
            AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null && p.getHealth() > attr.getValue()) {
                p.setHealth(attr.getValue());
            }
            return true;
        } catch (Exception ex) {
            if (!warned) {
                warned = true;
                getLogger().log(Level.WARNING, "Gagal reload HP AuraSkills untuk " + p.getName(), ex);
            }
            return false;
        }
    }

    /** Cari handler HP AuraSkills lewat reflection (tanpa dependensi compile). */
    private boolean init() {
        if (reloadMethod != null) return true;
        if (initTried) return false;
        initTried = true;
        try {
            Plugin as = Bukkit.getPluginManager().getPlugin("AuraSkills");
            if (as == null || !as.isEnabled()) {
                getLogger().warning("AuraSkills tidak aktif, sinkronisasi dimatikan.");
                return false;
            }
            ClassLoader cl = as.getClass().getClassLoader();
            Class<?> traits = Class.forName("dev.aurelium.auraskills.api.trait.Traits", true, cl);
            hpTrait = traits.getField("HP").get(null);

            Object traitManager = as.getClass().getMethod("getTraitManager").invoke(as);
            Method getImpl = null;
            for (Method m : traitManager.getClass().getMethods()) {
                if (m.getName().equals("getTraitImpl") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isInstance(hpTrait)) {
                    getImpl = m;
                    break;
                }
            }
            if (getImpl == null) throw new NoSuchMethodException("getTraitImpl(Trait)");
            hpImpl = getImpl.invoke(traitManager, hpTrait);

            for (Method m : hpImpl.getClass().getMethods()) {
                if (m.getName().equals("reload") && m.getParameterCount() == 2
                        && m.getParameterTypes()[0] == Player.class
                        && m.getParameterTypes()[1].isInstance(hpTrait)) {
                    reloadMethod = m;
                    break;
                }
            }
            if (reloadMethod == null) throw new NoSuchMethodException("reload(Player, Trait)");
            getLogger().info("Terhubung ke AuraSkills HP trait.");
            return true;
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Gagal terhubung ke AuraSkills (versi tidak cocok?)", ex);
            return false;
        }
    }
}
