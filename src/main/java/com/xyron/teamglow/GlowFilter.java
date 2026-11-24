package com.xyron.teamglow;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Only loaded when ProtocolLib exists. Keeps the glow flag only for teammates. */
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

    static void refresh(Player p) {
        ProtocolLibrary.getProtocolManager().updateEntity(p, new ArrayList<>(Bukkit.getOnlinePlayers()));
    }
}
