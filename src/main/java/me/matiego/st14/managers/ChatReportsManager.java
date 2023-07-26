package me.matiego.st14.managers;

import io.papermc.paper.network.ChannelInitializeListenerHolder;
import me.matiego.st14.utils.ChatPacketHandler;
import net.kyori.adventure.key.Key;

//Based on https://github.com/e-im/FreedomChat/tree/9e58cde2d95d6c472eb28f93c7acb1063e069964
public class ChatReportsManager {
    private final String NAMESPACE = "st14_chat";
    private final Key listenerKey = Key.key(NAMESPACE, "listener");

    public void start() {
        stop();
        ChannelInitializeListenerHolder.addListener(listenerKey, channel -> channel.pipeline().addAfter("packet_handler", NAMESPACE, new ChatPacketHandler()));
    }

    public void stop() {
        if (ChannelInitializeListenerHolder.hasListener(listenerKey)) {
            ChannelInitializeListenerHolder.removeListener(listenerKey);
        }
    }
}
