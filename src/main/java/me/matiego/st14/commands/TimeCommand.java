package me.matiego.st14.commands;

import lombok.ToString;
import me.matiego.st14.Main;
import me.matiego.st14.utils.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.*;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ToString
public class TimeCommand implements CommandHandler.Discord, CommandHandler.Minecraft {
    private final Main plugin;
    private final PluginCommand command;
    public TimeCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("time");
        if (command == null) {
            Logs.warning("The command /time does not exist in the plugin.yml file and cannot be registered.");
        }
    }
    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0 && sender instanceof Player player) {
            sendSelfTimes(player);
            return 5;
        }
        if (args.length == 1) {
            if (sender instanceof Player player && args[0].equalsIgnoreCase(player.getName())) {
                sendSelfTimes(player);
                return 5;
            }
            Utils.async(() -> {
                UUID uuid = plugin.getOfflinePlayers().getIdByName(args[0]);
                if (uuid == null) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.TIME + "&cNieznany gracz."));
                    return;
                }
                PlayerTime time = plugin.getTimeManager().retrieveTime(uuid);
                if (time == null) {
                    sender.sendMessage(Utils.getComponentByString(Prefix.TIME + "&cNapotkano niespodziewany b????d. Spr??buj ponownie."));
                    return;
                }
                boolean console = !(sender instanceof Player);
                sender.sendMessage(Utils.getComponentByString(
                        Prefix.TIME + "&6==============================\n" +
                        Prefix.TIME + "&6&lCzasy gracza &6" + args[0] + "&e:\n" +
                        Prefix.TIME + "&6Aktualny czas&e: " + formatMinecraft(time.getSession(), console) + "\n" +
                        Prefix.TIME + "&6Czas dzienny &e: " + formatMinecraft(time.getDaily(), console) + "\n" +
                        Prefix.TIME + "&6????czny czas  &e: " + formatMinecraft(time.getTotal(), console) + "\n" +
                        Prefix.TIME + "&6=============================="
                ));
            });
            return 5;
        }
        return -1;
    }

    private void sendSelfTimes(@NotNull Player player) {
        Utils.async(() -> {
            PlayerTime time = plugin.getTimeManager().retrieveTime(player.getUniqueId());
            if (time == null) {
                player.sendMessage(Utils.getComponentByString(Prefix.TIME + "&cNapotkano niespodziewany b????d. Spr??buj ponownie."));
                return;
            }
            player.sendMessage(Utils.getComponentByString(
                    Prefix.TIME + "&6==============================\n" +
                    Prefix.TIME + "&6&lTwoje czasy:\n" +
                    Prefix.TIME + "&6Aktualny czas&e: " + formatMinecraft(time.getSession(), true) + "\n" +
                    Prefix.TIME + "&6Czas dzienny &e: " + formatMinecraft(time.getDaily(), true) + "\n" +
                    Prefix.TIME + "&6????czny czas  &e: " + formatMinecraft(time.getTotal(), true) + "\n" +
                    Prefix.TIME + "&6=============================="
            ));
        });
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("time", "Wy??wietla czasy gracza")
                .addOptions(
                        new OptionData(OptionType.STRING, "gracz", "Gracz, kt??rego czasy maj?? by?? wy??wietlone", true)
                                .setAutoComplete(true),
                        new OptionData(OptionType.STRING, "incognito", "czy wiadomo???? ma by?? widoczna tylko dla ciebie", false)
                                .addChoice("Tak", "True")
                                .addChoice("Nie", "False")
                )
                .setGuildOnly(true);
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        boolean ephemeral = event.getOption("incognito", "False", OptionMapping::getAsString).equals("True");
        event.deferReply(ephemeral).queue();
        String name = event.getOption("gracz", "", OptionMapping::getAsString);
        InteractionHook hook = event.getHook();
        Utils.async(() -> {
            UUID uuid = plugin.getOfflinePlayers().getIdByName(name);
            if (uuid == null) {
                hook.sendMessage("Nieznany gracz.").queue();
                return;
            }
            UUID linkedAccount = plugin.getAccountsManager().getPlayerByUser(event.getUser());
            boolean self = linkedAccount != null && linkedAccount.equals(uuid) && ephemeral;

            PlayerTime time = plugin.getTimeManager().retrieveTime(uuid);
            if (time == null) {
                hook.sendMessage("Napotkano niespodziewany b????d. Spr??buj ponownie.").queue();
                return;
            }

            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle(self ? "Twoje czasy gry" : "Czasy gry " + name);
            if (self) {
                eb.addField("Aktualny czas", formatDiscord(time.getSession(), true), false);
            } else {
                eb.addField("Aktualny czas", formatDiscord(time.getFakeSession(), false), false);
            }
            eb.addField("Czas dzienny", formatDiscord(time.getDaily(), self), false);
            eb.addField("????czny czas", formatDiscord(time.getTotal(), self), false);
            eb.setColor(Color.YELLOW);
            eb.setTimestamp(Instant.now());
            hook.sendMessageEmbeds(eb.build()).queue();
        });
        return 5;
    }

    private @NotNull String formatDiscord(@NotNull GameTime time, boolean showIncognito) {
        if (time.equals(GameTime.empty())) {
            return "Gracz jest offline";
        }
        String result = "Razem: `" + Utils.parseMillisToString(time.getNormal() + time.getAfk() + (showIncognito ? time.getIncognito() : 0), false) + "` w tym:\n" +
                " - Czas zwyk??y: `" + Utils.parseMillisToString(time.getNormal(), false) + "`\n" +
                " - Czas AFK: `" + Utils.parseMillisToString(time.getAfk(), false) + "`";
        if (showIncognito) {
            result += "\n - Czas incognito: `" + Utils.parseMillisToString(time.getIncognito(), false) + "`";
        }
        return result;
    }

    private @NotNull String formatMinecraft(@NotNull GameTime time, boolean showIncognito) {
        if (time.equals(GameTime.empty())) {
            return "&cGracz jest offline";
        }
        String result = Utils.parseMillisToString(time.getNormal() + time.getAfk() + (showIncognito ? time.getIncognito() : 0), false) +
                " &6[&e" +
                Utils.parseMillisToString(time.getNormal(), false) + " &6|&e " +
                Utils.parseMillisToString(time.getAfk(), false);
        if (showIncognito) {
            result += " &6|&e " + Utils.parseMillisToString(time.getIncognito(), false);
        }
        return result + "&6]";
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return plugin.getOfflinePlayers().getNames();
        }
        return new ArrayList<>();
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteraction event) {
        if (!event.getName().equals(getDiscordCommand().getName())) return;
        event.replyChoices(plugin.getOfflinePlayers().getNames().stream()
                .filter(name -> name.toLowerCase().startsWith(event.getFocusedOption().getValue().toLowerCase()))
                .map(name -> new Command.Choice(name, name))
                .collect(Collectors.toList())
        ).queue();
    }
}
