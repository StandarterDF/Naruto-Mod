package com.sekwah.narutomod.network.c2s;

import com.mojang.authlib.GameProfile;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.friends.FriendsSavedData;
import com.sekwah.narutomod.network.s2c.ClientboundFriendNoticePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Everything a player can do to their friends list: ask, accept, refuse, remove, cancel.
 *
 * <p>The name is only used for a request - the others carry the uuid of the line that was clicked.
 * A request resolves an online player first and falls back to the server's profile cache, so a
 * friend can be asked while they are offline and the request will be waiting when they log in. Every
 * check lives here: the screen is not trusted to have asked for anything sensible.</p>
 */
public class ServerFriendActionPacket {

    public static final byte REQUEST = 0;
    public static final byte ACCEPT = 1;
    public static final byte DECLINE = 2;
    public static final byte REMOVE = 3;
    public static final byte CANCEL = 4;

    private final byte action;
    @Nullable
    private final UUID target;
    private final String name;

    public ServerFriendActionPacket(byte action, @Nullable UUID target, String name) {
        this.action = action;
        this.target = target;
        this.name = name == null ? "" : name;
    }

    public static ServerFriendActionPacket request(String name) {
        return new ServerFriendActionPacket(REQUEST, null, name);
    }

    public static ServerFriendActionPacket accept(UUID target) {
        return new ServerFriendActionPacket(ACCEPT, target, "");
    }

    public static ServerFriendActionPacket decline(UUID target) {
        return new ServerFriendActionPacket(DECLINE, target, "");
    }

    public static ServerFriendActionPacket remove(UUID target) {
        return new ServerFriendActionPacket(REMOVE, target, "");
    }

    public static ServerFriendActionPacket cancel(UUID target) {
        return new ServerFriendActionPacket(CANCEL, target, "");
    }

    public static void encode(ServerFriendActionPacket msg, FriendlyByteBuf buffer) {
        buffer.writeByte(msg.action);
        buffer.writeBoolean(msg.target != null);
        if (msg.target != null) {
            buffer.writeUUID(msg.target);
        }
        buffer.writeUtf(msg.name);
    }

    public static ServerFriendActionPacket decode(FriendlyByteBuf buffer) {
        byte action = buffer.readByte();
        UUID target = buffer.readBoolean() ? buffer.readUUID() : null;
        return new ServerFriendActionPacket(action, target, buffer.readUtf());
    }

    public static class Handler {
        public static void handle(ServerFriendActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    return;
                }
                FriendsSavedData data = Friends.data(player.server);
                if (data == null) {
                    player.displayClientMessage(Component.translatable("naruto.friends.fail.disabled")
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
                switch (msg.action) {
                    case REQUEST -> request(player, data, msg.name.trim());
                    case ACCEPT -> accept(player, data, msg.target);
                    case DECLINE -> decline(player, data, msg.target);
                    case REMOVE -> remove(player, data, msg.target);
                    case CANCEL -> cancel(player, data, msg.target);
                    default -> {
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }

        private static void request(ServerPlayer player, FriendsSavedData data, String name) {
            if (name.isEmpty()) {
                return;
            }
            ServerPlayer online = player.server.getPlayerList().getPlayerByName(name);
            UUID targetId;
            String targetName;
            if (online != null) {
                targetId = online.getUUID();
                targetName = online.getGameProfile().getName();
            } else {
                Optional<GameProfile> cached = player.server.getProfileCache().get(name);
                if (cached.isEmpty()) {
                    player.displayClientMessage(Component.translatable("naruto.friends.fail.unknown", name)
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
                targetId = cached.get().getId();
                targetName = cached.get().getName();
            }
            if (targetId.equals(player.getUUID())) {
                player.displayClientMessage(Component.translatable("naruto.friends.fail.self")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            data.recordName(player.getUUID(), player.getGameProfile().getName());
            data.recordName(targetId, targetName);
            if (data.areFriends(player.getUUID(), targetId)) {
                player.displayClientMessage(Component.translatable("naruto.friends.fail.already", targetName)
                        .withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            // Asking somebody who has already asked you is the same as accepting them.
            if (data.accept(player.getUUID(), targetId)) {
                congratulate(player, data, targetId, targetName);
                return;
            }
            if (data.hasIncoming(targetId, player.getUUID())) {
                player.displayClientMessage(Component.translatable("naruto.friends.fail.pending", targetName)
                        .withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            if (!data.request(player.getUUID(), targetId)) {
                player.displayClientMessage(Component.translatable("naruto.friends.fail.pending", targetName)
                        .withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            player.displayClientMessage(Component.translatable(online == null
                            ? "naruto.friends.sent.offline" : "naruto.friends.sent", targetName)
                    .withStyle(ChatFormatting.GREEN), false);
            if (online != null) {
                Friends.notifyRequest(online, player.getGameProfile().getName());
            }
            Friends.sendState(player);
            if (online != null) {
                Friends.sendState(online);
            }
        }

        private static void accept(ServerPlayer player, FriendsSavedData data, @Nullable UUID target) {
            if (target == null) {
                return;
            }
            String targetName = data.nameOf(target);
            if (!data.accept(player.getUUID(), target)) {
                player.displayClientMessage(Component.translatable("naruto.friends.fail.stale")
                        .withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            congratulate(player, data, target, targetName);
        }

        private static void congratulate(ServerPlayer player, FriendsSavedData data, UUID target, String targetName) {
            Friends.notify(player, ClientboundFriendNoticePacket.ACCEPTED, targetName);
            ServerPlayer other = online(player, target);
            if (other != null) {
                Friends.notify(other, ClientboundFriendNoticePacket.ACCEPTED,
                        player.getGameProfile().getName());
            }
            Friends.sendState(player);
            if (other != null) {
                Friends.sendState(other);
            }
        }

        private static void decline(ServerPlayer player, FriendsSavedData data, @Nullable UUID target) {
            if (target == null || !data.decline(player.getUUID(), target)) {
                return;
            }
            ServerPlayer other = online(player, target);
            if (other != null) {
                Friends.notify(other, ClientboundFriendNoticePacket.DECLINED,
                        player.getGameProfile().getName());
                Friends.sendState(other);
            }
            Friends.sendState(player);
        }

        private static void remove(ServerPlayer player, FriendsSavedData data, @Nullable UUID target) {
            if (target == null || !data.areFriends(player.getUUID(), target)) {
                return;
            }
            String targetName = data.nameOf(target);
            data.removeFriend(player.getUUID(), target);
            Friends.notify(player, ClientboundFriendNoticePacket.REMOVED, targetName);
            ServerPlayer other = online(player, target);
            if (other != null) {
                Friends.notify(other, ClientboundFriendNoticePacket.REMOVED,
                        player.getGameProfile().getName());
                Friends.sendState(other);
            }
            Friends.sendState(player);
        }

        private static void cancel(ServerPlayer player, FriendsSavedData data, @Nullable UUID target) {
            if (target == null || !data.cancel(player.getUUID(), target)) {
                return;
            }
            ServerPlayer other = online(player, target);
            if (other != null) {
                Friends.notify(other, ClientboundFriendNoticePacket.CANCELLED,
                        player.getGameProfile().getName());
                Friends.sendState(other);
            }
            Friends.sendState(player);
        }

        @Nullable
        private static ServerPlayer online(ServerPlayer player, UUID id) {
            return player.server.getPlayerList().getPlayer(id);
        }
    }
}
