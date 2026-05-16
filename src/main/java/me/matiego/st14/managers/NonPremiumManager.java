package me.matiego.st14.managers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.objects.Pair;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.NonPremiumUtils;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.apache.commons.lang3.RandomStringUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.UUID;

public class NonPremiumManager {
    public NonPremiumManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    public static final String JOIN_NAME_PREFIX = "st14_";
    public static final String NAME_PREFIX = "+";

    private final Main plugin;
    private final HashMap<UUID, Long> expiration = new HashMap<>();
    private final HashMap<String, Pair<UUID, String>> playerName = new HashMap<>();

    public void addPacketListener() {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Login.Client.START) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                String name = event.getPacket().getStrings().read(0).toLowerCase();

                if (name.startsWith(JOIN_NAME_PREFIX)) {
                    event.setCancelled(true);
                    Logs.info("Skipping authorisation for player " + name);

                    Pair<UUID, String> newName = playerName.remove(name);
                    if (newName == null) {
                        kickLoginPlayer(event, "&cAby zagrać z konta non-premium, musisz rozpocząć sesję na serwerze Discord! Użyj komendy &l/nonpremium start");
                        return;
                    }

                    Long time = expiration.remove(newName.getFirst());
                    if (time == null || Utils.now() - time > 60 * 1000) {
                        kickLoginPlayer(event, "&cTwoja sesja wygasła!");
                        return;
                    }
                    newName.setSecond(NAME_PREFIX + newName.getSecond());


                    LuckPerms luckPerms = LuckPermsProvider.get();
                    luckPerms.getUserManager().loadUser(newName.getFirst(), newName.getSecond()).thenAcceptAsync(user -> Utils.async(() -> {
                        if (forceLoginSuccess(event, newName.getFirst(), newName.getSecond())) {
                            sendMessageToUser(newName.getFirst(), "Dołączyłeś na serwer, miłej gry!");
                        } else {
                            kickLoginPlayer(event, "&cNapotkano niespodziewany błąd. Spróbuj później.");
                        }
                    }), runnable -> Bukkit.getScheduler().runTask(plugin, runnable));

                }
            }
        });
    }

    private void kickLoginPlayer(@NotNull PacketEvent event, @NotNull String message) {
        message = message.replace("&", "§");
        try {
            PacketContainer disconnect = ProtocolLibrary.getProtocolManager()
                    .createPacket(PacketType.Login.Server.DISCONNECT);

            disconnect.getChatComponents().write(0, WrappedChatComponent.fromText(message));

            ProtocolLibrary.getProtocolManager().sendServerPacket(event.getPlayer(), disconnect);
        } catch (Exception e) {
            Logs.error("Failed to kick player", e);
        }
    }

    private boolean forceLoginSuccess(@NotNull PacketEvent event, @NotNull UUID uuid, @NotNull String playerName) {
        try {
            GameProfile mojangProfile = new GameProfile(uuid, playerName);

            Object tempPlayer = event.getPlayer();
            Method getInjectorMethod = tempPlayer.getClass().getMethod("getInjector");
            Object injector = getInjectorMethod.invoke(tempPlayer);

            Channel nettyChannel = null;
            for (Method method : injector.getClass().getMethods()) {
                if (Channel.class.isAssignableFrom(method.getReturnType()) && method.getParameterCount() == 0) {
                    nettyChannel = (Channel) method.invoke(injector);
                    break;
                }
            }
            if (nettyChannel == null) {
                for (Field field : injector.getClass().getDeclaredFields()) {
                    if (Channel.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        nettyChannel = (Channel) field.get(injector);
                        break;
                    }
                }
            }
            if (nettyChannel == null) {
                Logs.error("Could not find the Channel object");
                return false;
            }

            Connection nmsConnection = (Connection) nettyChannel.pipeline().get("packet_handler");
            if (nmsConnection == null) {
                Logs.error("Could not find the Connection object");
                return false;
            }

            PacketListener rawListener = nmsConnection.getPacketListener();
            if (rawListener instanceof ServerLoginPacketListenerImpl loginListener) {
                InetAddress address = ((InetSocketAddress) nmsConnection.channel.remoteAddress()).getAddress();
                com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(uuid, playerName);

                //noinspection UnstableApiUsage
                AsyncPlayerPreLoginEvent preLoginEvent = new AsyncPlayerPreLoginEvent(
                        playerName,
                        address,
                        address,
                        uuid,
                        false,
                        profile,
                        "",
                        null
                );
                Bukkit.getPluginManager().callEvent(preLoginEvent);

                if (preLoginEvent.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                    kickLoginPlayer(event, LegacyComponentSerializer.legacyAmpersand().serialize(preLoginEvent.kickMessage()));
                    return false;
                }

                Method startVerificationMethod = ServerLoginPacketListenerImpl.class.getDeclaredMethod("startClientVerification", GameProfile.class);
                startVerificationMethod.setAccessible(true);
                startVerificationMethod.invoke(loginListener, mojangProfile);
                return true;
            } else {
                Logs.error("Expected ServerLoginPacketListenerImpl object, but found: " + (rawListener == null ? "null" : rawListener.getClass().getName()));
                return false;
            }
        } catch (Exception e) {
            Logs.error("Failed to authorise the player " + playerName, e);
        }
        return false;
    }

    public synchronized @Nullable String startLogin(@NotNull Member member, @NotNull String name) {
        if (!name.startsWith(JOIN_NAME_PREFIX)) return null;
        if (playerName.containsKey(name)) return null;

        User user = member.getUser();
        if (!user.getDiscriminator().equals("0000")) return null;
        if (!DiscordUtils.hasRole(member, plugin.getConfig().getLong("discord.role-ids.verified"))) return null;
        if (!DiscordUtils.hasRole(member, plugin.getConfig().getLong("discord.role-ids.non-premium"))) return null;

        UUID uuid = NonPremiumUtils.createNonPremiumUuid(member);
        if (!uuid.equals(plugin.getAccountsManager().getPlayerByUser(member))) return null;

        endSession(uuid, "Rozpoczęto nową sesję");

        String code = RandomStringUtils.secure().nextAlphanumeric(10);

        playerName.put(name, new Pair<>(uuid, user.getName()));
        expiration.put(uuid, Utils.now());

        Logs.info("Rozpoczęto sesję non-premium dla gracza " + DiscordUtils.getAsTag(member));

        return code;
    }

    public synchronized boolean isNameUsed(@NotNull String name) {
        return playerName.containsKey(name);
    }

    public synchronized void endSession(@NotNull UUID uuid, @NotNull String reason) {
        if (!NonPremiumUtils.isNonPremiumUuid(uuid)) return;

        playerName.entrySet().removeIf(e -> e.getValue().getFirst().equals(uuid));
        expiration.remove(uuid);

        Utils.sync(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            player.kick(Utils.getComponentByString("&cTwoja sesja została zakończona!\nPowód: " + reason));
        });
    }

    public void sendMessageToUser(@NotNull UUID uuid, @NotNull String message) {
        JDA jda = plugin.getJda();
        if (jda == null) return;

        jda.retrieveUserById(NonPremiumUtils.getIdByNonPremiumUuid(uuid)).queue(
                user -> DiscordUtils.sendPrivateMessage(user, message, action -> action.setComponents(ActionRow.of(Button.danger("end-session", "Zakończ sesję"))), result -> {})
        );
    }
}
