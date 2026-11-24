package com.xyron.teamglow;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Only loaded when ProtocolLib exists.
 * The server never marks anyone as glowing. Glow is sent ONLY to teammates.
 */
final class GlowFilter {

    static void register(TeamGlow owner) {
        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(owner, ListenerPriority.HIGH, PacketType.Play.Server.ENTITY_METADATA) {
                    @Override
                    public void onPacketSending(PacketEvent e) {
                        if (e.isPlayerTemporary()) return;
                        int id = e.getPacket().getIntegers().read(0);
                        String team = owner.teamOfEntity(id);
                        if (team == null) return;
                        boolean visible = owner.isGlow() && team.equals(owner.teamOfUuid(e.getPlayer().getUniqueId()));
                        PacketContainer packet = e.getPacket().deepClone();
                        List<WrappedDataValue> values = packet.getDataValueCollectionModifier().read(0);
                        for (WrappedDataValue v : values) {
                            if (v.getIndex() == 0 && v.getValue() instanceof Byte b) {
                                v.setValue(visible ? (byte) (b | 0x40) : (byte) (b & ~0x40));
                            }
                        }
                        packet.getDataValueCollectionModifier().write(0, values);
                        e.setPacket(packet);
                    }
                });
    }

    /** Tell every online player whether they should see this player glowing (teammates only). */
    static void sync(TeamGlow owner, Player target) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        String team = owner.teamOf(target);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean visible = team != null && owner.isGlow() && team.equals(owner.teamOf(viewer));
            try {
                PacketContainer pkt = pm.createPacket(PacketType.Play.Server.ENTITY_METADATA);
                pkt.getIntegers().write(0, target.getEntityId());
                List<WrappedDataValue> list = new ArrayList<>();
                list.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), flags(target, visible)));
                pkt.getDataValueCollectionModifier().write(0, list);
                pm.sendServerPacket(viewer, pkt, false);
            } catch (Exception ignored) { }
        }
    }

    private static byte flags(Player p, boolean glow) {
        int f = 0;
        if (p.getFireTicks() > 0) f |= 0x01;
        if (p.isSneaking()) f |= 0x02;
        if (p.isSprinting()) f |= 0x08;
        if (p.isSwimming()) f |= 0x10;
        if (p.isInvisible()) f |= 0x20;
        if (glow) f |= 0x40;
        if (p.isGliding()) f |= 0x80;
        return (byte) f;
    }
}
