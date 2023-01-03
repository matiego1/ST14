package me.matiego.st14.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.matiego.st14.IncognitoManager;
import me.matiego.st14.Main;
import me.matiego.st14.PremiumManager;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Prefixes;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PlayerListener implements Listener {

    private final Main plugin;
    public PlayerListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final HashMap<UUID, BukkitTask> disablingIncognito = new HashMap<>();
    private final HashMap<UUID, Player> sleepingPlayers = new HashMap<>();
    private final HashMap<UUID, BossBar> positionBossBars = new HashMap<>();

    @EventHandler
    public void onAsyncPlayerPreLogin(@NotNull AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        //check if login is successfully
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        if (Bukkit.hasWhitelist() && Bukkit.getWhitelistedPlayers().stream()
                .map(OfflinePlayer::getUniqueId)
                .noneMatch(u -> u.equals(uuid))) {
            return;
        }
        //refresh offline players names
        if (event.getName().length() > 36) {
            disallow(event, "&cTwój nick jest za długi! Może mieć maksymalnie 36 znaków.");
            return;
        }
        plugin.getOfflinePlayers().refresh(uuid, event.getName());
        //check if Discord bot is online
        JDA jda = plugin.getJda();
        if (jda == null) {
            disallow(event, Prefixes.DISCORD + "&cBot na Discord jest offline! Nie możesz dołączyć do serwera.");
            return;
        }
        if (Utils.getTps() < 15.0 && !plugin.getPremiumManager().isSuperPremium(uuid)) {
            disallow(event, "&cSerwer jest przeciążony - TPS spadły poniżej 15\nSpróbuj dołączyć później\n&7Przepraszamy");
            return;
        }
        //check if player has linked account
        if (!plugin.getAccountsManager().isRequired(uuid)) return;
        UserSnowflake id = plugin.getAccountsManager().getUserByPlayer(uuid);
        if (id == null) {
            String code = plugin.getAccountsManager().getNewVerificationCode(uuid);
            disallow(event, Prefixes.DISCORD + "\n" +
                    "Nie połączyłeś konta Discord z twoim kontem minecraft!\n\n" +
                    "Użyj komendy &9/accounts &bna Discord\n" +
                    "z kodem &9" + code + "&b\n\n" +
                    "&cUWAGA! &bKod będzie ważny tylko 5 minut.");
            return;
        }
        Guild guild = jda.getGuildById(plugin.getConfig().getLong("discord.guild-id"));
        if (guild == null) {
            Logs.warning("A guild id in the config file is not correct.");
            return;
        }
        Member member = guild.retrieveMember(id).complete();
        if (member == null) {
            disallow(event, Prefixes.DISCORD + "Wygląda na to, że nie ma cię na naszym serwerze Discord! Dołącz do niego, aby grać na tym serwerze.");
            return;
        }
        Role role = guild.getRoleById(plugin.getConfig().getLong("discord.role-id"));
        if (role == null) {
            Logs.warning("A role id in the config file is not correct.");
            return;
        }
        if (!member.getRoles().contains(role)) {
            plugin.getAccountsManager().unlink(uuid);
            disallow(event, Prefixes.DISCORD + "Twoje konto zostało rozłączone przed administratora. Dołącz ponownie, aby je połączyć.");
        }
    }
    private void disallow(@NotNull AsyncPlayerPreLoginEvent event, @NotNull String msg) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Utils.getComponentByString(msg));
    }

    @EventHandler (priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLoginMonitor(@NotNull AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        IncognitoManager manager = plugin.getIncognitoManager();
        manager.getIncognitoPlayers().stream()
                .filter(uuid -> !uuid.equals(event.getUniqueId()))
                .filter(manager::isKickingEnabled)
                .filter(uuid -> !manager.getTrustedPlayers(uuid).contains(event.getUniqueId()))
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(player -> Utils.sync(() -> player.kick(Utils.getComponentByString(Prefixes.INCOGNITO + "Gracz " + event.getName() + " dołącza do gry. Zostałeś wyrzucony, ponieważ masz włączony tryb incognito."))));
    }

    @EventHandler
    public void onPlayerLoginEvent(@NotNull PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.KICK_FULL) return;

        UUID uuid = event.getPlayer().getUniqueId();
        PremiumManager manager = plugin.getPremiumManager();
        if (manager.isPremium(uuid) && manager.makeSpaceForPlayer(uuid)) {
            event.allow();
        }
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        //load player times
        if (!plugin.getTimeManager().join(player)) {
            player.kick(Utils.getComponentByString("&cNapotkano niespodziewany błąd przy ładowaniu twoich czasów. Spróbuj ponownie."));
            return;
        }
        //incognito
        disablingIncognito.remove(uuid);
        if (plugin.getIncognitoManager().isIncognito(uuid)) {
            player.sendMessage(Utils.getComponentByString(Prefixes.INCOGNITO + "Jesteś incognito!"));
        }
        //premium
        long time = plugin.getPremiumManager().getRemainingTime(uuid);
        if (time > 0) {
            player.sendMessage(Utils.getComponentByString(Prefixes.PREMIUM + "Jesteś graczem premium! Twój status premium wygaśnie za &6" + Utils.parseMillisToString(time, false) + "&d."));
        }
        //join messages
        event.joinMessage(Utils.getComponentByString("&eGracz " + player.getName() + " dołączył do gry"));
        plugin.getChatMinecraft().sendJoinMessage(player);
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        plugin.getTellCommand().removeReply(player.getUniqueId());
        plugin.getTpaCommand().cancel(player);
        positionBossBars.remove(player.getUniqueId());
        plugin.getAfkManager().move(player);
        plugin.getBackpackManager().clearCache(player.getUniqueId());
        //quit message
        event.quitMessage(Utils.getComponentByString("&eGracz " + player.getName() + " opuścił grę"));
        plugin.getChatMinecraft().sendQuitMessage(player);
        //save player times
        plugin.getTimeManager().quit(player);
        //incognito
        disablingIncognito.put(
                player.getUniqueId(),
                Bukkit.getScheduler().runTaskLater(
                        plugin,
                        () -> {
                            plugin.getIncognitoManager().setIncognito(player.getUniqueId(), false);
                            disablingIncognito.remove(player.getUniqueId());
                        },
                        1200
                )
        );
    }

    @EventHandler
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        //noinspection deprecation
        plugin.getChatMinecraft().sendDeathMessage(event.getDeathMessage() == null ? event.getPlayer().getName() + " umarł" : event.getDeathMessage(), event.getPlayer());
    }

    @EventHandler
    public void onPlayerChat(@NotNull AsyncChatEvent event) {
        Player player = event.getPlayer();
        Block block = player.getLocation().getBlock();
        final Component message = event.message().replaceText(
                TextReplacementConfig
                        .builder()
                        .matchLiteral("[here]")
                        .replacement("[" + Utils.getWorldName(player.getWorld()) + ": " + block.getX() + ", " + block.getY() + ", " + block.getZ() + "]")
                        .once()
                        .build()
        );
        event.renderer((p1, p2, p3, p4) ->
                Utils.getComponentByString("&a[" + Utils.getWorldPrefix(player.getWorld()) + "] &f")
                .append(player.displayName())
                .append(Utils.getComponentByString("&f: "))
                .append(message)
        );
        plugin.getChatMinecraft().sendChatMessage(PlainTextComponentSerializer.plainText().serialize(message), player);
    }

    @EventHandler
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Utils.getComponentByString("&cNie możesz się teleportować do innych światów!"));
            }
        }
    }

    @EventHandler (ignoreCancelled = true)
    public void onPlayerCommandPreprocess(@NotNull PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().substring(1).toLowerCase();
        if (command.equalsIgnoreCase("minecraft:stop")) {
            event.setCancelled(true);
            player.performCommand("st14:stop");
        }
        if (command.isBlank()) return;
        if (command.charAt(0) == '/') command = command.substring(1);

        if (player.isOp()) {
            Logs.info("[" + player.getName() + "]: /" + command);
            return;
        }

        List<String> allowedCommands = plugin.getConfig().getStringList("allowed-commands");
        if (allowedCommands.isEmpty()) {
            Logs.info("[" + player.getName() + "]: /" + command);
            return;
        }

        for (String allowedCommand : allowedCommands) {
            if (command.startsWith(allowedCommand.toLowerCase())) {
                Logs.info("[" + player.getName() + "]: /" + command);
                return;
            }
        }
        event.setCancelled(true);
        player.sendMessage(Utils.getComponentByString("&cNie masz uprawnień, aby użyć tej komendy!"));
    }

    @EventHandler
    public void onPlayerCommandSent(@NotNull PlayerCommandSendEvent event) {if (event.getPlayer().isOp()) return;
        List<String> allowedCommands = plugin.getConfig().getStringList("allowed-commands");
        if (allowedCommands.isEmpty()) return;

        Iterator<String> iterator = event.getCommands().iterator();
        while (iterator.hasNext()) {
            String command = iterator.next();
            if (command.isBlank()) return;
            if (command.charAt(0) == '/') command = command.substring(1);
            if (!allowedCommands.contains(command)) iterator.remove();
        }
    }

    @EventHandler (ignoreCancelled = true)
    public void onPlayerBedEnter(@NotNull PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        Player player = event.getPlayer();
        UUID uuid = event.getBed().getWorld().getUID();
        if (sleepingPlayers.get(uuid) != null) {
            event.setCancelled(true);
            player.sendMessage(Utils.getComponentByString("&cW tym świecie już ktoś spi"));
            return;
        }
        sleepingPlayers.put(uuid, player);
    }

    @EventHandler (priority = EventPriority.MONITOR)
    public void onPlayerBedLeave(@NotNull PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        World world = event.getBed().getWorld();
        sleepingPlayers.remove(world.getUID());
        //noinspection SpellCheckingInspection
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(world))
                .forEach(p -> p.sendMessage(Utils.getComponentByString("&eGracz &6" + player.getName() + "&eposzedł spać. Słodkich snów!")));
        if (!plugin.getIncognitoManager().isIncognito(player.getUniqueId())) {
            plugin.getChatMinecraft().sendMessage("Przesypianie nocy", "[" + Utils.getWorldName(world) + "] Gracz **" + player + "** poszedł spać. Słodkich snów!");
        }
    }

    @EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        if (event.hasChangedBlock()) {
            Player player = event.getPlayer();
            if (player.isGliding()) {
                BossBar bar = positionBossBars.get(player.getUniqueId());
                if (bar == null) {
                    bar = BossBar.bossBar(Utils.getComponentByString(""), 1, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
                    player.showBossBar(bar);
                    positionBossBars.put(player.getUniqueId(), bar);
                }
                Block block = player.getLocation().getBlock();
                final BossBar finalBar = bar;
                Utils.async(() -> finalBar.name(Utils.getComponentByString("&6X: &e" + block.getX() + " &6Y: &e" + block.getY() + " &6Z: &e" + block.getZ())));
            } else {
                BossBar bar = positionBossBars.remove(player.getUniqueId());
                if (bar != null) {
                    player.hideBossBar(bar);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerUse(@NotNull PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR) return;

        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (item == null) return;
        if (!getItemName(item).equals("&9Banknot")) return;
        List<String> lores = getItemLore(item);
        if (lores.size() != 3) return;
        if (!lores.get(0).equals("&bKliknij PPM, trzymając w ręku,")) return;
        if (!lores.get(1).equals("&baby wpłacić")) return;
        if (!lores.get(2).startsWith("&bWartość: &9")) return;

        Player player = event.getPlayer();
        if (!Main.getInstance().getConfig().getStringList("economy-worlds").contains(player.getWorld().getName())) return;

        double amount;
        try {
            amount = Double.parseDouble(lores.get(2).replaceFirst(Pattern.quote("&bWartość: &9"), "").replaceFirst(Pattern.quote("$"), ""));
        }catch(Exception e) {
            return;
        }

        EconomyResponse response = plugin.getEconomy().depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            player.sendMessage(Utils.getComponentByString(Prefixes.ECONOMY + "Napotkano niespodziewany błąd. Spróbuj później."));
            return;
        }

        player.sendMessage(Utils.getComponentByString(Prefixes.ECONOMY + "Pomyślnie wpłacono pieniądze na twoje konto!"));
        player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
    }

    private @NotNull String getItemName(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "";
        Component name = meta.displayName();
        if (name == null) return "";
        return LegacyComponentSerializer.legacyAmpersand().serialize(name);
    }

    private @NotNull List<String> getItemLore(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return new ArrayList<>();
        List<Component> lores = meta.lore();
        if (lores == null) return new ArrayList<>();
        return lores.stream().map(lore -> LegacyComponentSerializer.legacyAmpersand().serialize(lore)).collect(Collectors.toList());
    }
}
