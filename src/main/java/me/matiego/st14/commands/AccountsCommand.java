package me.matiego.st14.commands;

import me.matiego.st14.AccountsManager;
import me.matiego.st14.Main;
import me.matiego.st14.utils.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class AccountsCommand implements CommandHandler.Discord, CommandHandler.Minecraft {
    private final Main plugin;
    private final PluginCommand command;
    public AccountsCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = Main.getInstance().getCommand("accounts");
        if (command == null) {
            Logs.warning("The command /accounts does not exist in the plugin.yml file and cannot be registered.");
        }
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("accounts", "Połącz twoje konta minecraft i Discord")
                .addOptions(
                        new OptionData(OptionType.STRING, "code", "twój kod weryfikacyjny", false)
                                .setRequiredLength(6, 6)
                )
                .setGuildOnly(true);
    }

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        String code = event.getOption("code", OptionMapping::getAsString);
        AccountsManager manager = plugin.getAccountsManager();
        User user = event.getUser();
        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        Utils.async(() -> {
            if (code != null) {
                if (manager.isLinked(user)) {
                    hook.sendMessage("Twoje konto już jest połączone z kontem minecraft.").queue();
                    return;
                }
                UUID uuid = manager.checkVerificationCode(code);
                if (uuid == null) {
                    hook.sendMessage("Twój kod jest niepoprawny. Aby wygenerować nowy, dołącz do serwera.").queue();
                    return;
                }
                if (manager.isLinked(uuid)) {
                    hook.sendMessage("To konto minecraft już jest połączone z jakimś kontem Discord.").queue();
                    return;
                }
                if (manager.link(uuid, user)) {
                    hook.sendMessage("Pomyślnie połączono twoje konta!").queue();
                    MessageEmbed embed = getEmbed(user);
                    if (embed == null) {
                        DiscordUtils.sendPrivateMessage(user, "Twoje konto zostało połączone z kontem minecraft! Niestety z powodu niespodziewanego błędu nie możemy dostarczyć Ci więcej informacji.");
                        return;
                    }
                    user.openPrivateChannel().queue(chn -> chn.sendMessageEmbeds(embed)
                            .addActionRow(
                                    Button.danger("unlink-accounts", "Rozłącz konta")
                                            .withEmoji(Emoji.fromUnicode("U+1F4A3"))
                            )
                            .queue(
                                    success -> {},
                                    failure -> {
                                        if (failure instanceof ErrorResponseException e && e.getErrorCode() == 50007) {
                                            Logs.warning("User " + user.getAsTag() + " doesn't allow private messages.");
                                        } else {
                                            Logs.error("An error occurred while sending a private message.", failure);
                                        }
                                    }
                            ));
                } else {
                    hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj ponownie.").queue();
                }
                return;
            }

            if (!manager.isLinked(user)) {
                hook.sendMessage("Twoje konto nie jest jeszcze połączone z kontem minecraft! Aby je połączyć, użyj komendy `/accounts` w grze.").queue();
                return;
            }
            MessageEmbed embed = getEmbed(user);
            if (embed == null) {
                hook.sendMessage("Twoje konto jest połączone z kontem minecraft! Niestety z powodu niespodziewanego błędu nie możemy dostarczyć Ci więcej informacji. Spróbuj później.").queue();
                return;
            }
            hook.sendMessageEmbeds(embed)
                    .addActionRow(
                            Button.danger("unlink-accounts", "Rozłącz konta")
                                    .withEmoji(Emoji.fromUnicode("U+1F4A3"))
                    )
                    .queue();
        });
        return 3;
    }

    private @Nullable MessageEmbed getEmbed(@NotNull UserSnowflake id) {
        UUID uuid = plugin.getAccountsManager().getPlayerByUser(id);
        if (uuid == null) return null;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Twoje konto minecraft:");
        eb.setDescription("**Nick:** `" + plugin.getOfflinePlayers().getEffectiveNameById(uuid) + "`\n**UUID:** `" + uuid + "`");
        eb.setColor(Color.BLUE);
        eb.setTimestamp(Instant.now());
        eb.setThumbnail(Utils.getSkinUrl(uuid));
        return eb.build();
    }

    @Override
    public int onButtonInteraction(@NotNull ButtonInteraction event) {
        if (!event.getComponentId().equals("unlink-accounts")) return 0;

        event.deferReply(true).queue();
        User user = event.getUser();
        AccountsManager manager = plugin.getAccountsManager();
        InteractionHook hook = event.getHook();

        Utils.async(() -> {
            event.editButton(event.getButton().asDisabled()).queue();
            if (!manager.isLinked(user)) {
                hook.sendMessage("Twoje konto nie jest jeszcze połączone z kontem minecraft! Aby je połączyć, dołącz do serwera").queue();
                return;
            }
            UUID uuid = manager.getPlayerByUser(user);
            if (uuid == null) {
                hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj później.").queue();
                return;
            }
            if (manager.unlink(uuid)) {
                hook.sendMessage("Pomyślnie rozłączono twoje konta!").queue();
            } else {
                hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj później.").queue();
            }
        });
        return 3;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString(Prefix.DISCORD + "&cTej komendy może użyć tylko gracz."));
            return 0;
        }
        if (args.length != 0) return -1;
        AccountsManager manager = plugin.getAccountsManager();
        UUID uuid = player.getUniqueId();

        Utils.async(() -> {
            Inventory inv = GUI.createInventory(9, Prefix.DISCORD + "Twoje konta");
            JDA jda = plugin.getJda();
            if (manager.isLinked(uuid)) {
                UserSnowflake id = manager.getUserByPlayer(uuid);
                if (id == null) {
                    //noinspection SpellCheckingInspection
                    inv.setItem(1, GUI.createGuiItem(
                            Material.LEAD,
                            "&9Konto Discord",
                            "&aTwoje konto jest połączone z kontem Discord!",
                            "&cNapotkano niespodziewany błąd przy",
                            "&cwczytywaniu informacji o twoim koncie",
                            "&9Kliknij, aby rozłączyć twoje konta"

                    ));
                } else {
                    String user = jda == null ? "&cBRAK" : jda.retrieveUserById(id.getId()).complete().getAsTag();
                    inv.setItem(1, GUI.createGuiItem(
                            Material.LEAD,
                            "&9Konto Discord",
                            "&aTwoje konto jest połączone z kontem Discord!",
                            "&bNick: " + user,
                            "&bID: " + id.getId(),
                            "&9Kliknij, aby rozłączyć twoje konta"

                    ));
                }
            } else {
                inv.setItem(1, GUI.createGuiItem(Material.LEAD, "&9Konto Discord", "&bKliknij, aby połączyć twoje konta"));
            }
            if (jda == null) {
                inv.setItem(4, GUI.createGuiItem(Material.REDSTONE, "&9Bot Discord", "&bAktualny status: &cOFFLINE"));
            } else {
                inv.setItem(4, GUI.createGuiItem(
                        Material.REDSTONE,
                        "&9Bot Discord",
                        "&bAktualny status: &aONLINE",
                        "&bNick: &9" + jda.getSelfUser().getAsTag()
                ));
            }
            inv.setItem(7, GUI.createGuiItem(Material.PAPER, "&9Serwer Discord", "&bKliknij, aby wyświetlić zaproszenie!"));
            Utils.sync(() -> player.openInventory(inv));
        });
        return 5;
    }

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!GUI.checkInventory(event, Prefix.DISCORD + "Twoje konta")) return;

        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();
        int slot = event.getSlot();
        ItemStack item = event.getCurrentItem();
        AccountsManager manager = plugin.getAccountsManager();
        Objects.requireNonNull(item); //already checked in GUI#checkInventory()

        if (slot == 7) {
            player.sendMessage(Utils.getComponentByString(Prefix.DISCORD + "Link z zaproszeniem na nasz serwer Discord: " + plugin.getConfig().getString("discord.invite-link", "&cBRAK")));
        } else if (slot == 1) {
            player.closeInventory();
            Utils.async(() -> {
                if (manager.isLinked(uuid)) {
                    UserSnowflake id = manager.getUserByPlayer(uuid);
                    boolean success = manager.unlink(uuid);
                    player.sendMessage(Utils.getComponentByString(Prefix.DISCORD + (success ?
                            "Pomyślnie rozłączono twoje konto z kontem Discord" :
                            "Napotkano niespodziewany błąd. Spróbuj później"
                    )));
                    JDA jda = plugin.getJda();
                    if (jda == null || id == null) return;
                    if (!success) return;
                    User user = jda.retrieveUserById(id.getId()).complete();
                    if (user == null) return;
                    DiscordUtils.sendPrivateMessage(user, "Twoje konto zostało rozłączone z kontem minecraft!");
                } else {
                    String code = plugin.getAccountsManager().getNewVerificationCode(uuid);
                    player.sendMessage(Utils.getComponentByString(
                            Prefix.DISCORD + "=================================\n" +
                            Prefix.DISCORD + "Aby dokończyć proces łączenia kont,\n" +
                            Prefix.DISCORD + "użyj komendy &9/accounts&b\n" +
                            Prefix.DISCORD + "na Discord z kodem: &9" + code + "&b.\n" +
                            Prefix.DISCORD + "UWAGA! Kod będzie ważny tylko 5 minut.\n" +
                            Prefix.DISCORD + "=================================\n"
                    ));
                }
            });
        }
    }
}
