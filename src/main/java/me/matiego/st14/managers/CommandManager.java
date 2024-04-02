package me.matiego.st14.managers;

import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;
import me.matiego.st14.Logs;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.objects.FixedSizeMap;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.context.UserContextInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class CommandManager extends ListenerAdapter implements CommandExecutor, TabCompleter, Listener {
    public CommandManager(@NotNull JDA jda, @NotNull List<CommandHandler> handlers) {
        List<CommandData> dc = new ArrayList<>();
        handlers.forEach(handler -> {
            if (handler instanceof CommandHandler.Discord discord) {
                CommandData data = discord.getDiscordCommand();
                if (!CommandHandler.Discord.isCommandTypeSupported(data.getType())) {
                    throw new UnsupportedOperationException("unsupported Discord command type: " + data.getType());
                }
                discordCommands.put(getCommandName(data), discord);
                dc.add(data);
            }
            if (handler instanceof CommandHandler.Minecraft minecraft) {
                PluginCommand cmd = minecraft.getMinecraftCommand();
                if (cmd == null) return;
                cmd.setExecutor(this);
                cmd.setTabCompleter(this);
                minecraftCommands.put(cmd.getName().toLowerCase(), minecraft);
            }
        });
        jda.updateCommands().addCommands(dc).queue();
    }

    private final HashMap<String, CommandHandler.Discord> discordCommands = new HashMap<>();
    private final HashMap<String, CommandHandler.Minecraft> minecraftCommands = new HashMap<>();
    private final FixedSizeMap<String, Long> minecraftCooldown = new FixedSizeMap<>(100);
    private final FixedSizeMap<String, Long> discordCooldown = new FixedSizeMap<>(100);

    @Getter (onMethod_ = {@Synchronized})
    @Setter (onMethod_ = {@Synchronized})
    private boolean enabled = false;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player && !isEnabled()) {
            player.sendMessage(Utils.getComponentByString("&cKomendy są aktualnie wyłączone. Spróbuj później."));
            return true;
        }
        //get handler
        CommandHandler.Minecraft handler = minecraftCommands.get(command.getName().toLowerCase());
        if (handler == null) {
            sender.sendMessage(Utils.getComponentByString("&cNieznana komenda."));
            return true;
        }
        //check cooldown
        if (sender instanceof Player player) {
            long time = getRemainingCooldown(command.getName(), player.getUniqueId());
            if (time > 0) {
                player.sendMessage(Utils.getComponentByString("&cTej komendy możesz użyć za " + Utils.parseMillisToString(time, false) + "."));
                return true;
            }
        }
        //execute command
        try {
            int cooldown = handler.onCommand(sender, args);
            if (cooldown < 0) return false;
            if (sender instanceof Player player && cooldown > 0) {
                putCooldown(command.getName(), player.getUniqueId(), cooldown);
            }
            return true;
        } catch (Exception e) {
            sender.sendMessage(Utils.getComponentByString("&cNapotkano niespodziewany błąd. Spróbuj później."));
            Logs.error("An error occurred while executing a command.", e);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return minecraftCommands.get(command.getName()).onTabComplete(sender, args).stream()
                .filter(complete -> complete.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        User user = event.getUser();
        String command = event.getName();

        if (!isEnabled()) {
            event.reply("Komendy są aktualnie wyłączone. Spróbuj później.").setEphemeral(true).queue();
            return;
        }

        //check permissions
        if (!DiscordUtils.hasRequiredPermissions(event.getChannel())) {
            event.reply("Nie mam wymaganych uprawnień na tym kanale.").setEphemeral(true).queue();
            return;
        }
        //get handler
        CommandHandler.Discord handler = discordCommands.get(command);
        if (handler == null) {
            event.reply("Nieznana komenda.").setEphemeral(true).queue();
            return;
        }
        //check cooldown
        long time = getRemainingCooldown(getCommandName(handler.getDiscordCommand()), user);
        if (time > 0) {
            event.reply("Tej komendy możesz użyć za " + Utils.parseMillisToString(time, false) + ".").setEphemeral(true).queue();
            return;
        }

        Logs.info(DiscordUtils.getAsTag(user) + " [" + user.getId() + "]: /" + event.getFullCommandName());
        //execute command
        try {
            int cooldown = handler.onSlashCommandInteraction(event.getInteraction());
            if (cooldown > 0) putCooldown(getCommandName(handler.getDiscordCommand()), user, cooldown);
        } catch (Exception e) {
            event.reply("Napotkano niespodziewany błąd. Spróbuj później.").setEphemeral(true).queue(success -> {}, failure -> {});
            Logs.error("An error occurred while executing a command.", e);
        }
    }

    @Override
    public void onUserContextInteraction(@NotNull UserContextInteractionEvent event) {
        if (!isEnabled()) {
            event.reply("Komendy są aktualnie wyłączone. Spróbuj później.").setEphemeral(true).queue();
            return;
        }

        UserContextInteraction interaction = event.getInteraction();
        int cooldown = -1;
        for (CommandHandler.Discord handler : discordCommands.values()) {
            try {
                cooldown = handler.onUserContextInteraction(interaction);
            } catch (Exception ignored) {}
            if (interaction.isAcknowledged()) {
                if (cooldown > 0) putCooldown(getCommandName(handler.getDiscordCommand()), event.getUser(), cooldown);
                return;
            }
        }
        event.reply("Nieznana komenda.").setEphemeral(true).queue();
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (!isEnabled()) {
            event.reply("Komendy są aktualnie wyłączone. Spróbuj później.").setEphemeral(true).queue();
            return;
        }

        ModalInteraction interaction = event.getInteraction();
        int cooldown = -1;
        for (CommandHandler.Discord handler : discordCommands.values()) {
            try {
                cooldown = handler.onModalInteraction(interaction);
            } catch (Exception ignored) {}
            if (interaction.isAcknowledged()) {
                if (cooldown > 0) putCooldown(getCommandName(handler.getDiscordCommand()), event.getUser(), cooldown);
                return;
            }
        }
        event.reply("Nieznana komenda.").setEphemeral(true).queue();
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!isEnabled()) {
            event.reply("Komendy są aktualnie wyłączone. Spróbuj później.").setEphemeral(true).queue();
            return;
        }

        ButtonInteraction interaction = event.getInteraction();
        int cooldown = -1;
        for (CommandHandler.Discord handler : discordCommands.values()) {
            try {
                cooldown = handler.onButtonInteraction(interaction);
            } catch (Exception ignored) {}
            if (interaction.isAcknowledged()) {
                if (cooldown > 0) putCooldown(getCommandName(handler.getDiscordCommand()), event.getUser(), cooldown);
                return;
            }
        }
        event.reply("Nieznana komenda.").setEphemeral(true).queue();
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        if (!isEnabled()) return;

        CommandAutoCompleteInteraction interaction = event.getInteraction();
        for (CommandHandler.Discord handler : discordCommands.values()) {
            try {
                handler.onCommandAutoCompleteInteraction(interaction);
            } catch (Exception ignored) {}
            if (interaction.isAcknowledged()) return;
        }
        event.replyChoices(new ArrayList<>()).queue();
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        minecraftCommands.values().forEach(value -> value.onInventoryClick(event));
    }

    private long getRemainingCooldown(@NotNull String command, @NotNull UserSnowflake id) {
        long now = Utils.now();
        return Math.max(0, discordCooldown.getOrDefault(command + "#" + id, now) - now);
    }
    private long getRemainingCooldown(@NotNull String command, @NotNull UUID uuid) {
        long now = Utils.now();
        return Math.max(0, minecraftCooldown.getOrDefault(command + "#" + uuid, now) - now);
    }

    private void putCooldown(@NotNull String command, @NotNull UserSnowflake id, int seconds) {
        discordCooldown.put(command + "#" + id, seconds * 1000L + Utils.now());
    }
    public void putCooldown(@NotNull String command, @NotNull UUID uuid, int seconds) {
        minecraftCooldown.put(command + "#" + uuid, seconds * 1000L + Utils.now());
    }
    private @NotNull String getCommandName(@NotNull CommandData data) {
        return (data.getType() == Command.Type.SLASH ? "" : data.getType().name() + "#") + data.getName();
    }
}
