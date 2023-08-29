package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public class EntityDamageByEntityListener implements Listener {
    public EntityDamageByEntityListener(@NotNull Main plugin) {
        this.plugin = plugin;
        key = new NamespacedKey(plugin, "projectile-damage");
    }
    private final Main plugin;
    private final NamespacedKey key;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Entity damager = event.getDamager();

        plugin.getAntyLogoutManager().putAntyLogout(player, damager);

        EntityType type = damager.getType();
        if (type != EntityType.SNOWBALL && type != EntityType.EGG) return;

        double damage = plugin.getConfig().getDouble("projectile-damage", event.getDamage());

        try {
            Double amount = damager.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
            if (amount != null) {
                damage = amount;
            }
        } catch (Exception ignored) {}

        event.setDamage(damage);
    }

    public @NotNull ItemStack getSnowball(double damage) {
        ItemStack item = new ItemStack(Material.SNOWBALL);

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, damage);
        item.setItemMeta(meta);

        return item;
    }

    public @NotNull ItemStack getEgg(double damage) {
        ItemStack item = new ItemStack(Material.SNOWBALL);

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, damage);
        item.setItemMeta(meta);

        return item;
    }

    public void copyItemStackDamageToProjectile(@NotNull Projectile projectile, @NotNull ItemStack item) {
        try {
            Double damage = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
            if (damage == null) return;
            projectile.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, damage);
        } catch (Exception ignored) {}
    }
}
