package com.sekwah.narutomod.friends;

import com.sekwah.narutomod.network.s2c.ClientboundFriendsPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every friendship on the server, plus the requests waiting for an answer.
 *
 * <p>Friendships are bidirectional by construction: the pair is stored on both players at the same
 * time, so there is no such thing as a one-sided friend. The data lives with the world rather than
 * in a player's own capabilities, because a removal has to reach the other side even while that
 * player is offline, and a request sent to somebody who is offline has to be waiting for them when
 * they next log in.</p>
 *
 * <p>Player names are remembered alongside the ids so the friends screen can list somebody who is
 * not online. Requests are kept with a timestamp, which is only used to show the oldest first; they
 * do not expire, since a request that quietly vanished would look exactly like a bug.</p>
 */
public class FriendsSavedData extends SavedData {

    public static final String DATA_NAME = "narutomod_friends";

    private static final String PLAYERS_TAG = "Players";
    private static final String REQUESTS_TAG = "Requests";
    private static final String NAMES_TAG = "Names";
    private static final String ID_TAG = "Id";
    private static final String NAME_TAG = "Name";
    private static final String FRIENDS_TAG = "Friends";
    private static final String FROM_TAG = "From";
    private static final String TO_TAG = "To";
    private static final String TIME_TAG = "Time";

    /** Accepted friendships, held on both sides. */
    private final Map<UUID, Set<UUID>> friends = new HashMap<>();

    /** Last known name of every player the server has seen or been asked about. */
    private final Map<UUID, String> names = new HashMap<>();

    /** Requests waiting for an answer: target -> requester -> when it was sent. */
    private final Map<UUID, Map<UUID, Long>> incoming = new HashMap<>();

    private Set<UUID> friendsOfInternal(UUID player) {
        return this.friends.computeIfAbsent(player, id -> new LinkedHashSet<>());
    }

    /** Remembers a player's name so an offline entry still has something to show. */
    public void recordName(UUID id, String name) {
        if (id == null || name == null || name.isBlank()) {
            return;
        }
        if (!name.equals(this.names.put(id, name))) {
            this.setDirty();
        }
    }

    /** @return the last known name, or a shortened uuid if the player was never seen. */
    public String nameOf(UUID id) {
        String name = this.names.get(id);
        if (name != null && !name.isBlank()) {
            return name;
        }
        String raw = id.toString();
        return raw.substring(0, 8);
    }

    /** @return an unmodifiable copy of a player's accepted friends. */
    public Set<UUID> friendsOf(UUID player) {
        Set<UUID> known = this.friends.get(player);
        return known == null ? Collections.emptySet() : Collections.unmodifiableSet(known);
    }

    public boolean areFriends(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) {
            return false;
        }
        Set<UUID> known = this.friends.get(first);
        return known != null && known.contains(second);
    }

    /** Makes two players friends. Both sides are written at once, which is what "bidirectional" means. */
    public void addFriend(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) {
            return;
        }
        boolean changed = this.friendsOfInternal(first).add(second);
        changed |= this.friendsOfInternal(second).add(first);
        if (changed) {
            this.setDirty();
        }
    }

    /** Drops a friendship on both sides, along with any half-finished request between them. */
    public void removeFriend(UUID first, UUID second) {
        boolean changed = this.removeOneWay(first, second);
        changed |= this.removeOneWay(second, first);
        changed |= this.clearRequest(first, second);
        changed |= this.clearRequest(second, first);
        if (changed) {
            this.setDirty();
        }
    }

    private boolean removeOneWay(UUID owner, UUID friend) {
        Set<UUID> known = this.friends.get(owner);
        return known != null && known.remove(friend);
    }

    /** @return true if the request was not already waiting. */
    public boolean request(UUID from, UUID to) {
        if (from == null || to == null || from.equals(to) || this.areFriends(from, to)) {
            return false;
        }
        Map<UUID, Long> waiting = this.incoming.computeIfAbsent(to, id -> new HashMap<>());
        if (waiting.containsKey(from)) {
            return false;
        }
        waiting.put(from, System.currentTimeMillis());
        this.setDirty();
        return true;
    }

    public boolean hasIncoming(UUID target, UUID requester) {
        Map<UUID, Long> waiting = this.incoming.get(target);
        return waiting != null && waiting.containsKey(requester);
    }

    public boolean hasOutgoing(UUID requester, UUID target) {
        return this.hasIncoming(target, requester);
    }

    /** @return the players waiting for this player to answer, oldest request first. */
    public List<UUID> incomingOf(UUID target) {
        Map<UUID, Long> waiting = this.incoming.get(target);
        if (waiting == null || waiting.isEmpty()) {
            return List.of();
        }
        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(waiting.entrySet());
        sorted.sort(Map.Entry.comparingByValue());
        List<UUID> result = new ArrayList<>(sorted.size());
        for (Map.Entry<UUID, Long> entry : sorted) {
            result.add(entry.getKey());
        }
        return result;
    }

    /** @return the players this one has asked and not heard back from yet, oldest first. */
    public List<UUID> outgoingOf(UUID requester) {
        List<Map.Entry<UUID, Long>> found = new ArrayList<>();
        for (Map.Entry<UUID, Map<UUID, Long>> entry : this.incoming.entrySet()) {
            Long time = entry.getValue().get(requester);
            if (time != null) {
                found.add(Map.entry(entry.getKey(), time));
            }
        }
        found.sort(Map.Entry.comparingByValue());
        List<UUID> result = new ArrayList<>(found.size());
        for (Map.Entry<UUID, Long> entry : found) {
            result.add(entry.getKey());
        }
        return result;
    }

    /** Accepts a request: the two become friends and the pending entry disappears. */
    public boolean accept(UUID target, UUID requester) {
        if (!this.hasIncoming(target, requester)) {
            return false;
        }
        this.clearRequest(target, requester);
        this.addFriend(target, requester);
        this.setDirty();
        return true;
    }

    /** Refuses a request. Nothing was ever a friendship, so only the pending pair is dropped. */
    public boolean decline(UUID target, UUID requester) {
        if (!this.hasIncoming(target, requester)) {
            return false;
        }
        this.clearRequest(target, requester);
        this.setDirty();
        return true;
    }

    /** Cancels one's own outgoing request, if it is still waiting. */
    public boolean cancel(UUID requester, UUID target) {
        return this.decline(target, requester);
    }

    /** Builds the packet a player's screen is drawn from: friends, requests in, requests out. */
    public ClientboundFriendsPacket packetFor(UUID player) {
        return new ClientboundFriendsPacket(
                Friends.entries(this, this.friendsOf(player)),
                Friends.entries(this, this.incomingOf(player)),
                Friends.entries(this, this.outgoingOf(player)));
    }

    private boolean clearRequest(UUID target, UUID requester) {
        Map<UUID, Long> waiting = this.incoming.get(target);
        if (waiting == null) {
            return false;
        }
        boolean removed = waiting.remove(requester) != null;
        if (waiting.isEmpty()) {
            this.incoming.remove(target);
        }
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag playerList = new ListTag();
        Set<UUID> everyone = new HashSet<>(this.friends.keySet());
        for (Set<UUID> known : this.friends.values()) {
            everyone.addAll(known);
        }
        for (UUID id : everyone) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(ID_TAG, id);
            Set<UUID> known = this.friends.get(id);
            if (known != null && !known.isEmpty()) {
                ListTag friendList = new ListTag();
                for (UUID friend : known) {
                    CompoundTag friendTag = new CompoundTag();
                    friendTag.putUUID(ID_TAG, friend);
                    friendList.add(friendTag);
                }
                entry.put(FRIENDS_TAG, friendList);
            }
            playerList.add(entry);
        }
        tag.put(PLAYERS_TAG, playerList);

        ListTag requestList = new ListTag();
        for (Map.Entry<UUID, Map<UUID, Long>> target : this.incoming.entrySet()) {
            for (Map.Entry<UUID, Long> requester : target.getValue().entrySet()) {
                CompoundTag request = new CompoundTag();
                request.putUUID(FROM_TAG, requester.getKey());
                request.putUUID(TO_TAG, target.getKey());
                request.putLong(TIME_TAG, requester.getValue());
                requestList.add(request);
            }
        }
        tag.put(REQUESTS_TAG, requestList);

        ListTag nameList = new ListTag();
        for (Map.Entry<UUID, String> name : this.names.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(ID_TAG, name.getKey());
            entry.putString(NAME_TAG, name.getValue());
            nameList.add(entry);
        }
        tag.put(NAMES_TAG, nameList);
        return tag;
    }

    public static FriendsSavedData load(CompoundTag tag) {
        FriendsSavedData data = new FriendsSavedData();
        ListTag names = tag.getList(NAMES_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < names.size(); i++) {
            CompoundTag entry = names.getCompound(i);
            if (entry.hasUUID(ID_TAG)) {
                data.names.put(entry.getUUID(ID_TAG), entry.getString(NAME_TAG));
            }
        }
        ListTag players = tag.getList(PLAYERS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            if (!entry.hasUUID(ID_TAG)) {
                continue;
            }
            UUID id = entry.getUUID(ID_TAG);
            data.friendsOfInternal(id).clear();
            ListTag friendList = entry.getList(FRIENDS_TAG, Tag.TAG_COMPOUND);
            for (int j = 0; j < friendList.size(); j++) {
                CompoundTag friend = friendList.getCompound(j);
                if (friend.hasUUID(ID_TAG)) {
                    data.friendsOfInternal(id).add(friend.getUUID(ID_TAG));
                }
            }
        }
        ListTag requests = tag.getList(REQUESTS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < requests.size(); i++) {
            CompoundTag request = requests.getCompound(i);
            if (request.hasUUID(FROM_TAG) && request.hasUUID(TO_TAG)) {
                data.incoming.computeIfAbsent(request.getUUID(TO_TAG), id -> new HashMap<>())
                        .put(request.getUUID(FROM_TAG), request.getLong(TIME_TAG));
            }
        }
        return data;
    }
}
