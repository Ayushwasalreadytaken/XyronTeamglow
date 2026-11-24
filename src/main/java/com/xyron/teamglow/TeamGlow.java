package com.xyron.teamglow;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeamGlow extends JavaPlugin implements Listener {

    public static final String PREFIX = "tg_";
    public static final List<ChatColor> PALETTE = List.of(
            ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW,
            ChatColor.AQUA, ChatColor.LIGHT_PURPLE, ChatColor.GOLD, ChatColor.DARK_PURPLE,
            ChatColor.WHITE, ChatColor.DARK_GREEN);

    private final Map<String, ChatColor> teamColors = new LinkedHashMap<>();
    private final Map<UUID, String> memberTeam = new ConcurrentHashMap<>();
    private final Map<Integer, String> entityTeam = new ConcurrentHashMap<>();
    private final Map<UUID, Scoreboard> personal = new ConcurrentHashMap<>();
    private boolean fight = false; // false = teammates can NOT hit each other
    private boolean glow = true;
    private boolean filterActive = false;
    private TeamCommand command;

    @Override
    public void onEnable() {
        command = new TeamCommand(this);

        // remove leftover teams from older versions (they were saved in the world and still color names)
        for (Team stale : new ArrayList<>(Bukkit.getScoreboardManager().getMainScoreboard().getTeams())) {
            if (stale.getName().startsWith(PREFIX)) stale.unregister();
        }
        PluginCommand pc = getCommand("team");
        if (pc != null) { pc.setExecutor(command); pc.setTabCompleter(command); }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTask(this, () -> Bukkit.getOnlinePlayers().forEach(this::rebuildView));

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (UUID id : memberTeam.keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) syncGlow(p);
            }
        }, 20L, 20L);

        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            try {
                GlowFilter.register(this);
                filterActive = true;
                getLogger().info("ProtocolLib found: glow is visible to teammates only.");
            } catch (Throwable t) {
                getLogger().warning("ProtocolLib failed (" + t + "). Glow is DISABLED for everyone.");
            }
        } else {
            getLogger().warning("ProtocolLib not found. Glow is DISABLED for everyone. Install ProtocolLib for team-only glow.");
        }
    }

    @Override
    public void onDisable() {
        for (String name : new ArrayList<>(teamColors.keySet())) deleteTeam(name);
        for (Player p : Bukkit.getOnlinePlayers()) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    // ---- make sure OUR /team runs instead of vanilla /team ----
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCmd(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage();
        String low = m.toLowerCase();
        if (low.equals("/team") || low.startsWith("/team ")) {
            e.setCancelled(true);
            String rest = m.length() > 6 ? m.substring(6).trim() : "";
            command.onCommand(e.getPlayer(), null, "team", rest.isEmpty() ? new String[0] : rest.split("\\s+"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsoleCmd(ServerCommandEvent e) {
        String m = e.getCommand();
        String low = m.toLowerCase();
        if (low.equals("team") || low.startsWith("team ")) {
            e.setCancelled(true);
            String rest = m.length() > 5 ? m.substring(5).trim() : "";
            command.onCommand(e.getSender(), null, "team", rest.isEmpty() ? new String[0] : rest.split("\\s+"));
        }
    }

    // ---------- API ----------
    public String teamOfEntity(int id) { return entityTeam.get(id); }
    public String teamOfUuid(UUID id) { return memberTeam.get(id); }
    public String teamOf(Player p) { return memberTeam.get(p.getUniqueId()); }
    public boolean teamExists(String name) { return teamColors.containsKey(name.toLowerCase()); }
    public Set<String> teamNames() { return teamColors.keySet(); }
    public ChatColor colorOf(String name) { return teamColors.get(name.toLowerCase()); }
    public boolean colorUsed(ChatColor c) { return teamColors.containsValue(c); }
    public boolean isFight() { return fight; }
    public boolean isGlow() { return glow; }
    public boolean isFilterActive() { return filterActive; }

    public ChatColor nextFreeColor() {
        for (ChatColor c : PALETTE) if (!teamColors.containsValue(c)) return c;
        return PALETTE.get(teamColors.size() % PALETTE.size());
    }

    public void createTeam(String name, ChatColor color) {
        name = name.toLowerCase();
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team old = sb.getTeam(PREFIX + name);
        if (old != null) old.unregister();
        Team t = sb.registerNewTeam(PREFIX + name);
        t.setAllowFriendlyFire(fight);
        t.setCanSeeFriendlyInvisibles(true);
        teamColors.put(name, color);
    }

    /** EVERY player gets a private scoreboard. Only their own teammates are colored on it. */
    private void rebuildView(Player v) {
        String team = memberTeam.get(v.getUniqueId());
        Scoreboard sb = personal.computeIfAbsent(v.getUniqueId(), k -> Bukkit.getScoreboardManager().getNewScoreboard());
        for (Team old : new ArrayList<>(sb.getTeams())) old.unregister();
        if (team != null) {
            Team t = sb.registerNewTeam("view");
            t.setColor(teamColors.get(team));
            t.setCanSeeFriendlyInvisibles(true);
            for (UUID id : members(team)) {
                String n = Bukkit.getOfflinePlayer(id).getName();
                if (n != null) t.addEntry(n);
            }
        }
        if (v.getScoreboard() != sb) v.setScoreboard(sb);
    }

    private void rebuildTeamViews(String team) {
        for (UUID id : members(team)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) rebuildView(p);
        }
    }

    public void addPlayer(Player p, String team) {
        team = team.toLowerCase();
        removePlayer(p);
        Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(PREFIX + team);
        if (t == null) return;
        t.addEntry(p.getName());
        memberTeam.put(p.getUniqueId(), team);
        entityTeam.put(p.getEntityId(), team);
        p.setGlowing(false); // server never glows; teammates get it via packets
        rebuildTeamViews(team);
        syncGlow(p);
    }

    public void removePlayer(Player p) {
        String team = memberTeam.remove(p.getUniqueId());
        entityTeam.remove(p.getEntityId());
        if (team == null) return;
        Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(PREFIX + team);
        if (t != null) t.removeEntry(p.getName());
        p.setGlowing(false);
        rebuildView(p);
        rebuildTeamViews(team);
        syncGlow(p);
    }

    public void deleteTeam(String name) {
        name = name.toLowerCase();
        for (UUID id : members(name)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) removePlayer(p); else memberTeam.remove(id);
        }
        Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(PREFIX + name);
        if (t != null) t.unregister();
        teamColors.remove(name);
    }

    public List<UUID> members(String team) {
        List<UUID> list = new ArrayList<>();
        memberTeam.forEach((u, t) -> { if (t.equalsIgnoreCase(team)) list.add(u); });
        return list;
    }

    public void setFight(boolean on) {
        fight = on;
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (String n : teamColors.keySet()) {
            Team t = sb.getTeam(PREFIX + n);
            if (t != null) t.setAllowFriendlyFire(on);
        }
    }

    public void setGlow(boolean on) {
        glow = on;
        for (UUID id : memberTeam.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) syncGlow(p);
        }
    }

    private void syncGlow(Player p) {
        if (!filterActive) return;
        try { GlowFilter.sync(this, p); } catch (Throwable ignored) { }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String team = memberTeam.get(p.getUniqueId());
        if (team != null) {
            Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(PREFIX + team);
            if (t != null) t.addEntry(p.getName());
            entityTeam.put(p.getEntityId(), team);
            p.setGlowing(false);
        }
        rebuildView(p); // clean private scoreboard for everyone
        if (team != null) syncGlow(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        entityTeam.remove(e.getPlayer().getEntityId());
        personal.remove(e.getPlayer().getUniqueId());
    }
}
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
