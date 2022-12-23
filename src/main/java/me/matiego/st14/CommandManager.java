package me.matiego.st14;

import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;
import me.matiego.st14.utils.CommandHandler;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.context.MessageContextInteraction;
import net.dv8tion.jda.api.interactions.commands.context.UserContextInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;
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

public class CommandManager extends ListenerAdapter implements CommandExecutor, TabCompleter, Listener {
    public CommandManager(@NotNull List<CommandHandler> handlers) {
        List<CommandData> dc = new ArrayList<>();
        handlers.forEach(handler -> {
            if (handler instanceof CommandHandler.Discord discord) {
                CommandData data = discord.getDiscordCommand();
                String pref = data.getType() == Command.Type.SLASH ? "" : "#";
                discordCommands.put(pref + data.getName(), discord);
                dc.add(data);
            }
            if (handler instanceof CommandHandler.Minecraft minecraft) {
                PluginCommand cmd = minecraft.getMinecraftCommand();
                if (cmd == null) return;
                cmd.setExecutor(this);
                cmd.setTabCompleter(this);
                minecraftCommands.put(cmd.getName(), minecraft);
            }
        });
        JDA jda = Main.getInstance().getJda();
        if (jda == null) throw new NullPointerException("JDA is null");
        jda.updateCommands().addCommands(dc).queue();
    }

    private final HashMap<String, CommandHandler.Discord> discordCommands = new HashMap<>();
    private final HashMap<String, CommandHandler.Minecraft> minecraftCommands = new HashMap<>();

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
        CommandHandler.Minecraft handler = minecraftCommands.get(command.getName());
        if (handler == null) {
            sender.sendMessage(Utils.getComponentByString("&cNieznana komenda."));
            return true;
        }
        //execute command
        try {
            return handler.onCommand(sender, args);
        } catch (Exception e) {
            sender.sendMessage(Utils.getComponentByString("&cNapotkano niespodziewany błąd. Spróbuj później."));
            Logs.error("An error occurred while executing a command.", e);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return minecraftCommands.get(command.getName()).onTabComplete(sender, args);
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        User user = event.getUser();
        String command = event.getName();

        if (!isEnabled()) {
            event.reply("Komendy są aktualnie wyłączone. Spróbuj później.").setEphemeral(true).queue();
            return;
        }

        Logs.info(user.getAsTag() + " [" + user.getId() + "]: /" + command);

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
        //execute command
        try {
            handler.onSlashCommandInteraction(event.getInteraction());
        } catch (Exception e) {
            event.reply("Napotkano niespodziewany błąd. Spróbuj później.").setEphemeral(true).queue(success -> {}, failure -> {});
            Logs.error("An error occurred while executing a command.", e);
        }
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        StringSelectInteraction interaction = event.getInteraction();
        for (CommandHandler.Discord handler : discordCommands.values()) {
            try {
                handler.onStringSelectInteraction(interaction);
            } catch (Exception ignored) {}
            if (interaction.isAcknowledged()) return;
        }
        event.reply("Nieznana komenda.").setEphemeral(true).queue();
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        ModalInteraction interaction = event.getInteraction();
        for (CommandHandler.Discord handler : discordCommands.values()) {
            try {
                handler.onModalInteraction(interaction);
            } catch (Exception ignored) {}
            if (interaction.isAcknowledged()) return;
        }
        event.reply("Nieznana komenda.").setEphemeral(true).queue();
    }

    @Override
    public void onMessageContextInteraction(@NotNull MessageContextInteractionEvent event) {
        MessageContextInteraction interaction = event.getInteraction();
        for (CommandHandler.Discord handler : discordCommands.values()) {
            try {
                handler.onMessageContextInteraction(interaction);
            } catch (Exception ignored) {}
            if (interaction.isAcknowledged()) return;
        }
        event.reply("Nieznana komenda.").setEphemeral(true).queue();
    }

    @Override
    public void onUserContextInteraction(@NotNull UserContextInteractionEvent event) {
        UserContextInteraction interaction = event.getInteraction();
        for (CommandHandler.Discord handler : discordCommands.values()) {
            try {
                handler.onUserContextInteraction(interaction);
            } catch (Exception ignored) {}
            if (interaction.isAcknowledged()) return;
        }
        event.reply("Nieznana komenda.").setEphemeral(true).queue();
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        ButtonInteraction interaction = event.getInteraction();
        for (CommandHandler.Discord handler : discordCommands.values()) {
            try {
                handler.onButtonInteraction(interaction);
            } catch (Exception ignored) {}
            if (interaction.isAcknowledged()) return;
        }
        event.reply("Nieznana komenda.").setEphemeral(true).queue();
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        minecraftCommands.values().forEach(value -> value.onInventoryClick(event));
    }
}
