package me.matiego.st14.commands;

import me.matiego.st14.AccountsManager;
import me.matiego.st14.IncognitoManager;
import me.matiego.st14.Main;
import me.matiego.st14.utils.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class IncognitoCommand implements CommandHandler.Minecraft, CommandHandler.Discord, Listener {
    private final Main plugin;
    private final PluginCommand command;
    public IncognitoCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("incognito");
        if (command == null) {
            Logs.warning("The command /incognito does not exist in the plugin.yml file and cannot be registered.");
        }
    }

    private final Material BLOCK_ON = Material.LIME_WOOL;
    private final Material BLOCK_OFF = Material.RED_WOOL;
    private final HashMap<UUID, Boolean> inventory = new HashMap<>();

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("incognito", "Zarządzaj statusem incognito twojego konta minecraft").setGuildOnly(true);
    }

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString(Prefix.INCOGNITO + "&cTej komendy może użyć tylko gracz."));
            return 0;
        }
        if (args.length != 0) return -1;
        IncognitoManager manager = plugin.getIncognitoManager();
        Inventory inv = GUI.createInventory(18, Prefix.INCOGNITO + "Ustawienia");
        inv.setItem(2, GUI.createGuiItem(Material.NAME_TAG, "&8Zaufaj graczowi", "&7Kliknij, aby zaufać nowemu graczowi!"));
        if (manager.isIncognito(player.getUniqueId())) {
            inventory.put(player.getUniqueId(), true);
            inv.setItem(4, GUI.createGuiItem(BLOCK_ON,  "&8Tryb incognito", "&7Status: &aON", "&7Kliknij, aby zmienić"));
        } else {
            inventory.put(player.getUniqueId(), false);
            inv.setItem(4, GUI.createGuiItem(BLOCK_OFF,  "&8Tryb incognito", "&7Status: &cOFF", "&7Kliknij, aby zmienić"));
        }
        Utils.async(() -> {
            if (manager.isKickingEnabled(player.getUniqueId())) {
                inv.setItem(6, GUI.createGuiItem(BLOCK_ON, "&8Wyrzucanie z serwera, gdy wchodzi gracz", "&7Status: &aON", "&7Kliknij, aby zmienić"));
            } else {
                inv.setItem(6, GUI.createGuiItem(BLOCK_OFF,  "&8Wyrzucanie z serwera, gdy wchodzi gracz", "&7Status: &cOFF", "&7Kliknij, aby zmienić"));
            }
        });
        Utils.async(() -> updateTrustedPlayers(manager.getTrustedPlayers(player.getUniqueId()), inv));
        player.openInventory(inv);
        return 5;
    }

    private void updateTrustedPlayers(@NotNull List<UUID> trusted, @NotNull Inventory inv) {
        int index = 9;
        for (UUID uuid : trusted) {
            if (index == 18) {
                inv.setItem(17, GUI.createGuiItem(Material.ARROW, "&8Ups...", "&7Ufasz za dużej ilości graczy!", "&7Na liście zostało wyświetlone pierwsze 8 pozycji"));
                return;
            }
            inv.setItem(index, GUI.createPlayerSkull(Bukkit.getOfflinePlayer(uuid), "&8" + plugin.getOfflinePlayers().getEffectiveNameById(uuid), "&7Ufasz temu graczowi!", "&7Kliknij, aby przestać mu ufać."));
            index++;
        }
        for (int i = index; i < 18; i++) {
            inv.setItem(i, new ItemStack(Material.AIR));
        }
        if (index == 9) inv.setItem(13, GUI.createGuiItem(Material.BARRIER, "&8Nie ufasz żadnym graczom!", "&7Kliknij na znacznik, aby zacząć komuś ufać!"));
    }

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!GUI.checkInventory(event, Prefix.INCOGNITO + "Ustawienia")) return;

        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();
        int slot = event.getSlot();
        Inventory inv = event.getView().getTopInventory();
        ItemStack item = event.getCurrentItem();
        IncognitoManager manager = plugin.getIncognitoManager();
        Objects.requireNonNull(item); //already checked in GUI#checkInventory()

        if (slot == 4) {
            if (item.getType() == BLOCK_ON) {
                inventory.put(uuid, false);
                inv.setItem(4, GUI.createGuiItem(BLOCK_OFF,  "&8Tryb incognito", "&7Status: &cOFF", "&7Kliknij, aby zmienić"));
            } else {
                inventory.put(uuid, true);
                inv.setItem(4, GUI.createGuiItem(BLOCK_ON,  "&8Tryb incognito", "&7Status: &aON", "&7Kliknij, aby zmienić"));
            }
        } else if (slot == 6) {
            if (item.getType() == BLOCK_ON) {
                Utils.async(() -> {
                    if (manager.setKickingEnabled(uuid, false)) inv.setItem(6, GUI.createGuiItem(BLOCK_OFF,  "&8Wyrzucanie z serwera, gdy wchodzi gracz", "&7Status: &cOFF", "&7Kliknij, aby zmienić"));
                });
            } else {
                Utils.async(() -> {
                    if (manager.setKickingEnabled(uuid, true)) inv.setItem(6, GUI.createGuiItem(BLOCK_ON, "&8Wyrzucanie z serwera, gdy wchodzi gracz", "&7Status: &aON", "&7Kliknij, aby zmienić"));
                });
            }
        } else if (slot == 2) {
            new AnvilGUI.Builder()
                    .title(ChatColor.translateAlternateColorCodes('&', Prefix.INCOGNITO + "Podaj nick gracza"))
                    .text("Podaj nick...")
                    .itemLeft(GUI.createGuiItem(Material.PAPER, "&8Wprowadź nick gracza...", "&7Kliknij &8ESC&7, aby wyjść", "&7Kliknij przedmiot po prawej, aby zaakceptować"))
                    .plugin(plugin)
                    .interactableSlots(AnvilGUI.Slot.INPUT_RIGHT)
                    .onComplete(completion -> {
                        UUID trustedUuid = plugin.getOfflinePlayers().getIdByName(completion.getText());
                        if (trustedUuid == null) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("Zły nick!"));
                        }
                        if (trustedUuid.equals(uuid)) {
                            return List.of(AnvilGUI.ResponseAction.replaceInputText("To twój nick!"));
                        }
                        Utils.async(() -> manager.addTrustedPlayer(uuid, trustedUuid));
                        player.sendMessage(Utils.getComponentByString(Prefix.INCOGNITO + "Pomyślnie zaufano nowemu graczowi"));
                        return List.of(AnvilGUI.ResponseAction.close());
                    })
                    .open(player);
        } else if (slot >= 9) {
            if (item.getType() == Material.BARRIER) return;
            UUID trustedUuid = plugin.getOfflinePlayers().getIdByName(PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(item.getItemMeta().displayName())));
            if (trustedUuid == null) {
                player.sendMessage(Utils.getComponentByString(Prefix.INCOGNITO + "Napotkano niespodziewany błąd. Spróbuj później."));
                player.closeInventory();
                return;
            }
            Utils.async(() -> {
                if (manager.removeTrustedPlayer(uuid, trustedUuid)) updateTrustedPlayers(manager.getTrustedPlayers(uuid), inv);
            });
        }
    }

    public void onInventoryClose(@NotNull UUID uuid) {
        Boolean value = inventory.get(uuid);
        if (value == null) return;
        plugin.getIncognitoManager().setIncognito(uuid, value);
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();
        AccountsManager accounts = plugin.getAccountsManager();
        IncognitoManager manager = plugin.getIncognitoManager();
        User user = event.getUser();
        Utils.async(() -> {
            if (!accounts.isLinked(user)) {
                hook.sendMessage("Twoje konto nie jest jeszcze połączone z kontem minecraft! Aby je połączyć, użyj komendy `/accounts` w grze.").queue();
                return;
            }
            UUID uuid = accounts.getPlayerByUser(user);
            if (uuid == null) {
                hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj później.").queue();
                return;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                hook.sendMessage("Jesteś online! Zmień swój status incognito komendą `/incognito` w grze.").queue();
                return;
            }
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("__**Ustawienia**__");
            eb.setColor(Color.LIGHT_GRAY);
            eb.setDescription("**Gracz:** `" + plugin.getOfflinePlayers().getEffectiveNameById(uuid) + "`");
            eb.addField("**Status incognito:**", manager.isIncognito(uuid) ? "`Włączone` :green_circle:" : "`Wyłączone` :red_circle:", false);
            eb.setFooter(DiscordUtils.getName(user, event.getMember()), DiscordUtils.getAvatar(user, event.getMember()));
            eb.setTimestamp(Instant.now());
            hook.sendMessageEmbeds(eb.build()).addActionRow(Button.secondary("change-inc-status", "Zmień status incognito")).queue();
        });
        return 5;
    }

    @Override
    public int onButtonInteraction(@NotNull ButtonInteraction event) {
        if (!event.getComponentId().equals("change-inc-status")) return 0;

        event.deferReply(true).queue();
        AccountsManager accounts = plugin.getAccountsManager();
        IncognitoManager manager = plugin.getIncognitoManager();
        User user = event.getUser();
        InteractionHook hook = event.getHook();
        event.editButton(event.getButton().asDisabled()).queue();

        Utils.async(() -> {
            if (!accounts.isLinked(user)) {
                hook.sendMessage("Twoje konto nie jest jeszcze połączone z kontem minecraft! Aby je połączyć, użyj komendy `/accounts` w grze.").queue();
                return;
            }
            UUID uuid = accounts.getPlayerByUser(user);
            if (uuid == null) {
                hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj później.").queue();
                return;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                hook.sendMessage("Jesteś online! Zmień swój status incognito komendą `/incognito` w grze.").queue();
                return;
            }
            manager.setIncognito(uuid, !manager.isIncognito(uuid));
            hook.sendMessage("Pomyślnie zmieniono twój status incognito! **Aktualny status incognito:** " + (manager.isIncognito(uuid) ? "`Włączone` :green_circle:" : "`Wyłączone` :red_circle:")).queue();
        });
        return 0;
    }
}
