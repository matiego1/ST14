package me.matiego.st14.commands;

import me.matiego.st14.Main;
import me.matiego.st14.utils.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.Instant;
import java.util.UUID;

public class TimeCommand implements CommandHandler.Discord, CommandHandler.Minecraft {
    private final Main plugin;
    private final org.bukkit.command.PluginCommand command;
    public TimeCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = Main.getInstance().getCommand("time");
        if (command == null) {
            Logs.warning("The command /time does not exist in the plugin.yml file and cannot be registered.");
        }
    }
    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0 && sender instanceof Player player) {
            sendSelfTimes(player);
            return true;
        }
        if (args.length == 1) {
            if (sender instanceof Player player && args[0].equalsIgnoreCase(player.getName())) {
                sendSelfTimes(player);
                return true;
            }
            Utils.async(() -> {
                UUID uuid = plugin.getOfflinePlayers().getIdByName(args[0]);
                if (uuid == null) {
                    sender.sendMessage(Utils.getComponentByString(Prefixes.TIME + "&cNieznany gracz."));
                    return;
                }
                PlayerTime time = plugin.getTimeManager().retrieveTime(uuid);
                if (time == null) {
                    sender.sendMessage(Utils.getComponentByString(Prefixes.TIME + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
                    return;
                }
                sender.sendMessage(Utils.getComponentByString(
                        Prefixes.TIME + "&6==============================\n" +
                        Prefixes.TIME + "&6&lCzasy gracza &6" + args[0] + "&e:\n" +
                        Prefixes.TIME + "&6Aktualny czas&e: " + formatMinecraft(time.getCurrent(), false) +
                        Prefixes.TIME + "&6Czas dzienny &e: " + formatMinecraft(time.getDaily(), false) +
                        Prefixes.TIME + "&6Łączny czas  &e: " + formatMinecraft(time.getTotal(), false) +
                        Prefixes.TIME + "&6=============================="
                ));
            });
        }
        return false;
    }

    private void sendSelfTimes(@NotNull Player player) {
        Utils.async(() -> {
            PlayerTime time = plugin.getTimeManager().retrieveTime(player.getUniqueId());
            if (time == null) {
                player.sendMessage(Utils.getComponentByString(Prefixes.TIME + "&cNapotkano niespodziewany błąd. Spróbuj ponownie."));
                return;
            }
            player.sendMessage(Utils.getComponentByString(
                    Prefixes.TIME + "&6==============================\n" +
                    Prefixes.TIME + "&6&lTwoje czasy:\n" +
                    Prefixes.TIME + "&6Aktualny czas&e: " + formatMinecraft(time.getCurrent(), true) + "\n" +
                    Prefixes.TIME + "&6Czas dzienny &e: " + formatMinecraft(time.getDaily(), true) + "\n" +
                    Prefixes.TIME + "&6Łączny czas  &e: " + formatMinecraft(time.getTotal(), true) + "\n" +
                    Prefixes.TIME + "&6=============================="
            ));
        });
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("time", "Wyświetla czasy gracza")
                .addOptions(
                        new OptionData(OptionType.STRING, "gracz", "Gracz, którego czasy mają być wyświetlone", true),
                        new OptionData(OptionType.STRING, "incognito", "czy wiadomość ma być widoczna tylko dla ciebie", false)
                                .addChoice("Tak", "True")
                                .addChoice("Nie", "False")
                );
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        boolean isEphemeral = event.getOption("incognito", "False", OptionMapping::getAsString).equals("True");
        event.deferReply(isEphemeral).queue();
        String name = event.getOption("gracz", "", OptionMapping::getAsString);
        InteractionHook hook = event.getHook();
        Utils.async(() -> {
            UUID uuid = plugin.getOfflinePlayers().getIdByName(name);
            if (uuid == null) {
                hook.sendMessage("Nieznany gracz.").queue();
                return;
            }
            UUID linkedAccount = plugin.getAccountsManager().getPlayerByUser(event.getUser());
            boolean isOwnAccount = linkedAccount != null && linkedAccount.equals(uuid) && isEphemeral;

            PlayerTime time = plugin.getTimeManager().retrieveTime(uuid);
            if (time == null) {
                hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj ponownie.").queue();
                return;
            }

            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle(isOwnAccount ? "Twoje czasy gry" : "Czasy gry " + name);
            eb.addField("Aktualny czas", time.getCurrent().equals(new GameTime(0, 0, 0)) ? "Gracz jest offline" : formatDiscord(time.getCurrent(), isOwnAccount), false);
            eb.addField("Czas dzienny", formatDiscord(time.getDaily(), isOwnAccount), false);
            eb.addField("Łączny czas", formatDiscord(time.getTotal(), isOwnAccount), false);
            eb.setColor(Color.YELLOW);
            eb.setTimestamp(Instant.now());
            hook.sendMessageEmbeds(eb.build()).queue();
        });
    }

    private @NotNull String formatDiscord(@NotNull GameTime time, boolean showIncognito) {
        String result = "Razem: `" + Utils.parseMillisToString((time.getNormal() + time.getAfk() + (showIncognito ? time.getIncognito() : 0)) * 1000L, false) + "` w tym:\n" +
                " - Czas zwykły: `" + Utils.parseMillisToString(time.getNormal() * 1000L, false) + "`\n" +
                " - Czas AFK: `" + Utils.parseMillisToString(time.getAfk() * 1000L, false) + "`";
        if (showIncognito) {
            result += "\n - Czas incognito: `" + Utils.parseMillisToString(time.getIncognito() * 1000L, false) + "`";
        }
        return result;
    }

    private @NotNull String formatMinecraft(@NotNull GameTime time, boolean showIncognito) {
        String result = Utils.parseMillisToString((time.getNormal() + time.getAfk() + (showIncognito ? time.getIncognito() : 0)) * 1000L, false) +
                " &6[&e" +
                Utils.parseMillisToString(time.getNormal() * 1000L, false) + " &6|&e " +
                Utils.parseMillisToString(time.getAfk() * 1000L, false);
        if (showIncognito) {
            result += " &6|&e " + Utils.parseMillisToString(time.getIncognito() * 1000L, false);
        }
        return result + "&6]";
    }
}
