package com.sekwah.narutomod.friends;

import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.network.PacketHandler;
import com.sekwah.narutomod.network.s2c.ClientboundFriendNoticePacket;
import com.sekwah.narutomod.network.s2c.ClientboundFriendsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The one place the rest of the mod asks whether two people are on the same side.
 *
 * <p>A friendship is a pact between two players: techniques do not land on a friend, and a clone
 * treats its caster's friends the way it treats its caster. Everything that picks a target or
 * filters a blast goes through this class, so there is a single answer instead of half a dozen
 * slightly different {@code isAlliedTo} checks.</p>
 *
 * <p>Clones inherit their caster's friends through {@link #playerId(Entity)}, which means a clone of
 * a friend is friendly too, and a clone of a stranger is not. The server is the only authority here;
 * on the client every answer is simply "no", because the friends data only exists on the server.</p>
 */
public final class Friends {

    private Friends() {
    }

    /** @return the saved friendships of a server, or null when the friends system is disabled. */
    @Nullable
    public static FriendsSavedData data(@Nullable MinecraftServer server) {
        if (server == null || !NarutoConfig.friendsEnabled) {
            return null;
        }
        return server.overworld().getDataStorage().computeIfAbsent(
                FriendsSavedData::load, FriendsSavedData::new, FriendsSavedData.DATA_NAME);
    }

    /** @return the saved friendships of a level, or null on the client. */
    @Nullable
    public static FriendsSavedData data(@Nullable Level level) {
        if (level == null || level.isClientSide) {
            return null;
        }
        return data(level.getServer());
    }

    /**
     * The player a creature answers to: itself for a player, the caster for a shadow clone. Anything
     * else (a zombie, an arrow, the world) has no id and can never be a friend.
     */
    @Nullable
    public static UUID playerId(@Nullable Entity entity) {
        if (entity instanceof Player player) {
            return player.getUUID();
        }
        if (entity instanceof ShadowCloneEntity clone) {
            return clone.getOwnerUUID().orElse(null);
        }
        return null;
    }

    /** @return whether the two creatures answer to players who are friends. Server side only. */
    public static boolean areFriends(@Nullable Entity first, @Nullable Entity second) {
        UUID firstId = playerId(first);
        UUID secondId = playerId(second);
        if (firstId == null || secondId == null || firstId.equals(secondId)) {
            return false;
        }
        FriendsSavedData data = data(first == null ? null : first.level());
        return data != null && data.areFriends(firstId, secondId);
    }

    /** @return whether the two players are friends according to the server. */
    public static boolean areFriends(@Nullable MinecraftServer server, @Nullable UUID first, @Nullable UUID second) {
        if (first == null || second == null || first.equals(second)) {
            return false;
        }
        FriendsSavedData data = data(server);
        return data != null && data.areFriends(first, second);
    }

    /**
     * Whether {@code candidate} belongs to {@code source}'s side: the same creature, a vanilla ally,
     * or a friend. This is what blasts and area effects filter with.
     */
    public static boolean isOwnSide(@Nullable Entity source, @Nullable Entity candidate) {
        if (source == null || candidate == null) {
            return false;
        }
        if (candidate == source || candidate.isAlliedTo(source)) {
            return true;
        }
        return areFriends(source, candidate);
    }

    /** @return true when this damage came from a mod technique rather than an ordinary blow. */
    public static boolean isTechniqueDamage(DamageSource source) {
        return source.typeHolder().unwrapKey()
                .map(key -> key.location().getNamespace().equals(com.sekwah.narutomod.NarutoMod.MOD_ID))
                .orElse(false);
    }

    /** Sends the player's whole friends picture to them. */
    public static void sendState(ServerPlayer player) {
        FriendsSavedData data = data(player.server);
        if (data == null) {
            return;
        }
        PacketHandler.sendToPlayer(data.packetFor(player.getUUID()), player);
    }

    /** Called when a player joins: remembers their name, syncs the screen and shows pending requests. */
    public static void onLogin(ServerPlayer player) {
        FriendsSavedData data = data(player.server);
        if (data == null) {
            return;
        }
        data.recordName(player.getUUID(), player.getGameProfile().getName());
        for (UUID requester : data.incomingOf(player.getUUID())) {
            notifyRequest(player, data.nameOf(requester));
        }
        sendState(player);
    }

    /** Shows the "add to friends" prompt to the target of a request. */
    public static void notifyRequest(ServerPlayer target, String requesterName) {
        PacketHandler.sendToPlayer(ClientboundFriendNoticePacket.request(requesterName), target);
    }

    /** Tells a player about an answer to a request, an accepted or removed friendship. */
    public static void notify(ServerPlayer player, byte type, String otherName) {
        PacketHandler.sendToPlayer(new ClientboundFriendNoticePacket(type, otherName), player);
    }

    /** Wraps ids in the name-carrying entries the screen expects. */
    public static List<ClientboundFriendsPacket.Entry> entries(FriendsSavedData data, Iterable<UUID> ids) {
        List<ClientboundFriendsPacket.Entry> entries = new ArrayList<>();
        for (UUID id : ids) {
            entries.add(new ClientboundFriendsPacket.Entry(id, data.nameOf(id)));
        }
        return entries;
    }
}
