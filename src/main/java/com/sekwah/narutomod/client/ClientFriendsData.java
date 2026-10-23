package com.sekwah.narutomod.client;

import com.sekwah.narutomod.network.s2c.ClientboundFriendNoticePacket;
import com.sekwah.narutomod.network.s2c.ClientboundFriendsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * The client's copy of the friends picture, filled by {@link ClientboundFriendsPacket}.
 *
 * <p>Only the screen reads it, so there is no per-tick work here. Notices are turned into chat lines
 * and, for a new request, a toast, which is what makes a request visible without opening the menu:
 * the player sees "X wants to add you as a friend" wherever they are.</p>
 */
public final class ClientFriendsData {

    private static List<ClientboundFriendsPacket.Entry> friends = List.of();
    private static List<ClientboundFriendsPacket.Entry> incoming = List.of();
    private static List<ClientboundFriendsPacket.Entry> outgoing = List.of();

    private ClientFriendsData() {
    }

    public static void apply(ClientboundFriendsPacket packet) {
        friends = List.copyOf(packet.friends());
        incoming = List.copyOf(packet.incoming());
        outgoing = List.copyOf(packet.outgoing());
    }

    public static List<ClientboundFriendsPacket.Entry> friends() {
        return friends;
    }

    public static List<ClientboundFriendsPacket.Entry> incoming() {
        return incoming;
    }

    public static List<ClientboundFriendsPacket.Entry> outgoing() {
        return outgoing;
    }

    public static boolean hasIncoming() {
        return !incoming.isEmpty();
    }

    public static void notice(byte type, String name) {
        Minecraft minecraft = Minecraft.getInstance();
        String key = switch (type) {
            case ClientboundFriendNoticePacket.REQUEST -> "naruto.friends.chat.request";
            case ClientboundFriendNoticePacket.ACCEPTED -> "naruto.friends.chat.accepted";
            case ClientboundFriendNoticePacket.DECLINED -> "naruto.friends.chat.declined";
            case ClientboundFriendNoticePacket.REMOVED -> "naruto.friends.chat.removed";
            case ClientboundFriendNoticePacket.CANCELLED -> "naruto.friends.chat.cancelled";
            default -> null;
        };
        if (key != null) {
            minecraft.gui.getChat().addMessage(Component.translatable(key, name));
        }
        if (type == ClientboundFriendNoticePacket.REQUEST) {
            SystemToast.add(minecraft.getToasts(), SystemToast.SystemToastIds.TUTORIAL_HINT,
                    Component.translatable("naruto.friends.toast.request.title"),
                    Component.translatable("naruto.friends.toast.request.body", name));
        }
    }

    /** @return whether this uuid is already on the friends list, used to colour rows. */
    public static boolean isFriend(UUID id) {
        for (ClientboundFriendsPacket.Entry entry : friends) {
            if (entry.id().equals(id)) {
                return true;
            }
        }
        return false;
    }
}
