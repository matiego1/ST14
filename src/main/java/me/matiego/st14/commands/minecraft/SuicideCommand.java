package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Main;
import me.matiego.st14.utils.CommandHandler;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Prefixes;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.UUID;

public class SuicideCommand implements CommandHandler.Minecraft {
    public SuicideCommand() {
        command = Main.getInstance().getCommand("suicide");
        if (command == null) {
            Logs.warning("The command /suicide does not exist in the plugin.yml file and cannot be registered.");
        }
    }
    private final PluginCommand command;
    private final HashMap<UUID, Long> suicides = new HashMap<>();

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Utils.getComponentByString(Prefixes.SUICIDE + "Tej komendy może użyć tylko gracz"));
            return 0;
        }
        suicides.put(player.getUniqueId(), Utils.now());
        player.setHealth(0);

        player.sendMessage(Utils.getComponentByString(Prefixes.SUICIDE + "&cPopełniłeś samobójstwo! Twój grób nie jest zabezpieczony."));
        Utils.async(() -> {
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player))
                    .forEach(p -> p.sendMessage(Utils.getComponentByString(Prefixes.SUICIDE + player.getName() + " popełnił samobójstwo!")));
            Bukkit.getConsoleSender().sendMessage(Utils.getComponentByString(Prefixes.SUICIDE + player.getName() + " popełnił samobójstwo!"));

            if (Main.getInstance().getIncognitoManager().isIncognito(player.getUniqueId())) return;
            Main.getInstance().getChatMinecraft().sendMessage("**" + player.getName() + "** popełnił samobójstwo!" , Prefixes.SUICIDE.getDiscord());
        });
        return 60;
    }

    public synchronized boolean isSuicide(@NotNull UUID uuid) {
        Long time = suicides.remove(uuid);
        if (time == null) return false;
        return (Utils.now() - time) <= 3000;
    }
}
