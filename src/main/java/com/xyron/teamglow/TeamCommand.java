package com.xyron.teamglow;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;

public class TeamCommand implements CommandExecutor, TabCompleter {

    private final TeamGlow plugin;

    public TeamCommand(TeamGlow plugin) { this.plugin = plugin; }

    private void msg(CommandSender s, String m) { s.sendMessage(ChatColor.GOLD + "[Team] " + ChatColor.RESET + m); }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) { help(sender); return true; }
        String sub = a[0].toLowerCase();

        if (sub.equals("list")) { list(sender); return true; }
        if (!sender.hasPermission("teamglow.admin")) { msg(sender, ChatColor.RED + "No permission."); return true; }

        switch (sub) {
            case "join" -> {
                if (a.length < 3) { msg(sender, "Usage: /team join <team> <players | @a except names>"); return true; }
                String name = a[1].toLowerCase().replaceAll("[^a-z0-9_\\-]", "");
                if (name.isEmpty() || name.length() > 12) { msg(sender, ChatColor.RED + "Team name must be 1-12 letters/numbers."); return true; }
                Set<Player> targets = resolve(sender, a, 2);
                if (targets.isEmpty()) { msg(sender, ChatColor.RED + "No players found."); return true; }
                if (!plugin.teamExists(name)) {
                    ChatColor c = colorFromName(name);
                    if (c == null || plugin.colorUsed(c)) c = plugin.nextFreeColor();
                    plugin.createTeam(name, c);
                }
                ChatColor color = plugin.colorOf(name);
                for (Player p : targets) {
                    plugin.addPlayer(p, name);
                    p.sendMessage(ChatColor.GOLD + "[Team] " + ChatColor.RESET + "You joined team " + color + name.toUpperCase());
                }
                msg(sender, "Added " + targets.size() + " player(s) to " + color + name.toUpperCase());
            }
            case "kick", "leave", "remove" -> {
                if (a.length < 2) { msg(sender, "Usage: /team kick <players>"); return true; }
                Set<Player> targets = resolve(sender, a, 1);
                for (Player p : targets) {
                    plugin.removePlayer(p);
                    p.sendMessage(ChatColor.GOLD + "[Team] " + ChatColor.RESET + "You were removed from your team.");
                }
                msg(sender, "Kicked " + targets.size() + " player(s).");
            }
            case "delete" -> {
                if (a.length < 2 || !plugin.teamExists(a[1])) { msg(sender, "Usage: /team delete <team>"); return true; }
                plugin.deleteTeam(a[1]);
                msg(sender, "Deleted team " + a[1].toLowerCase());
            }
            case "clear" -> {
                new ArrayList<>(plugin.teamNames()).forEach(plugin::deleteTeam);
                msg(sender, "All teams deleted.");
            }
            case "pvp", "fight" -> {
                if (a.length < 2) { msg(sender, "Teammates can hit each other: " + (plugin.isFight() ? "ON" : "OFF")); return true; }
                plugin.setFight(a[1].equalsIgnoreCase("on"));
                msg(sender, "Teammates can hit each other: " + (plugin.isFight() ? "ON" : "OFF"));
            }
            case "glow" -> {
                if (a.length < 2) { msg(sender, "Glow is " + (plugin.isGlow() ? "ON" : "OFF")); return true; }
                plugin.setGlow(a[1].equalsIgnoreCase("on"));
                msg(sender, "Team glow: " + (plugin.isGlow() ? "ON" : "OFF")
                        + (plugin.isFilterActive() ? "" : ChatColor.RED + " (ProtocolLib missing: everyone sees the glow)"));
            }
            default -> help(sender);
        }
        return true;
    }

    private void list(CommandSender sender) {
        boolean admin = sender.hasPermission("teamglow.admin");
        String own = (sender instanceof Player p) ? plugin.teamOf(p) : null;
        if (!admin && own == null) { msg(sender, "You are not in a team."); return; }
        if (plugin.teamNames().isEmpty()) { msg(sender, "No teams yet."); return; }
        for (String team : plugin.teamNames()) {
            if (!admin && !team.equalsIgnoreCase(own)) continue; // players only see their own team
            StringJoiner j = new StringJoiner(", ");
            for (UUID id : plugin.members(team)) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                j.add(op.getName() == null ? id.toString() : op.getName());
            }
            sender.sendMessage(plugin.colorOf(team) + team.toUpperCase() + ChatColor.GRAY + ": " + ChatColor.WHITE + j);
        }
    }

    /** Players, @a, @a[name=!X], or "@a except A B C". */
    private Set<Player> resolve(CommandSender s, String[] a, int from) {
        Set<Player> in = new LinkedHashSet<>();
        Set<String> except = new HashSet<>();
        boolean excluding = false;
        for (int i = from; i < a.length; i++) {
            String tok = a[i];
            if (tok.equalsIgnoreCase("except")) { excluding = true; continue; }
            if (excluding) { except.add(tok.toLowerCase()); continue; }
            if (tok.equalsIgnoreCase("@a")) {
                in.addAll(Bukkit.getOnlinePlayers());
            } else if (tok.startsWith("@")) {
                try {
                    for (Entity e : Bukkit.selectEntities(s, tok)) if (e instanceof Player p) in.add(p);
                } catch (IllegalArgumentException ex) { msg(s, ChatColor.RED + "Bad selector: " + tok); }
            } else {
                Player p = Bukkit.getPlayerExact(tok);
                if (p != null) in.add(p); else msg(s, ChatColor.RED + "Player not online: " + tok);
            }
        }
        in.removeIf(p -> except.contains(p.getName().toLowerCase()));
        return in;
    }

    private ChatColor colorFromName(String s) {
        try {
            ChatColor c = ChatColor.valueOf(s.toUpperCase());
            return c.isColor() ? c : null;
        } catch (IllegalArgumentException e) { return null; }
    }

    private void help(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "=== Team commands ===");
        s.sendMessage(ChatColor.YELLOW + "/team join <team> <player...>" + ChatColor.GRAY + " - add players (team is made automatically)");
        s.sendMessage(ChatColor.YELLOW + "/team join <team> @a except <names>" + ChatColor.GRAY + " - everyone except those names");
        s.sendMessage(ChatColor.YELLOW + "/team kick <player...>" + ChatColor.GRAY + " - remove players from their team");
        s.sendMessage(ChatColor.YELLOW + "/team list" + ChatColor.GRAY + " - show team members");
        s.sendMessage(ChatColor.YELLOW + "/team delete <team>" + ChatColor.GRAY + " - delete one team");
        s.sendMessage(ChatColor.YELLOW + "/team clear" + ChatColor.GRAY + " - delete all teams");
        s.sendMessage(ChatColor.YELLOW + "/team pvp <on|off>" + ChatColor.GRAY + " - can teammates hit each other (default off)");
        s.sendMessage(ChatColor.YELLOW + "/team glow <on|off>" + ChatColor.GRAY + " - team glow on/off");
        s.sendMessage(ChatColor.GRAY + "Tip: team names red, blue, green, yellow, aqua, gold... glow in that color.");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("join", "kick", "list", "delete", "clear", "pvp", "glow", "help"));
        else if (a.length == 2 && (a[0].equalsIgnoreCase("pvp") || a[0].equalsIgnoreCase("fight") || a[0].equalsIgnoreCase("glow"))) out.addAll(List.of("on", "off"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("delete")) out.addAll(plugin.teamNames());
        else if (a.length == 2 && a[0].equalsIgnoreCase("join")) { out.addAll(plugin.teamNames()); out.addAll(List.of("red", "blue", "green", "yellow")); }
        else { out.add("@a"); out.add("except"); Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName())); }
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
