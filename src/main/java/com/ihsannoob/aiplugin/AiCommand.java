package com.ihsannoob.aiplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileWriter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AiCommand implements CommandExecutor {

    private final AiPlugin plugin;

    public AiCommand(AiPlugin plugin) {
        this.plugin = plugin;
    }

    private void sendMsg(CommandSender sender, String msg) {
        sender.sendMessage("§6[AI] §r" + msg);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendMsg(sender, "Usage: /ai chat <message> | /ai view | /ai export | /ai clear | /ai reload");
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("reload")) {
            if (!sender.hasPermission("ai.reload") && !sender.isOp()) {
                sendMsg(sender, "You do not have permission to reload.");
                return true;
            }
            plugin.reloadCerebrasClient();
            sendMsg(sender, "Configuration reloaded.");
            return true;
        }

        if (sub.equals("chat")) {
            if (!(sender instanceof Player)) {
                sendMsg(sender, "Only players can use /ai chat.");
                return true;
            }
            if (args.length < 2) {
                sendMsg(sender, "Usage: /ai chat <message>");
                return true;
            }
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();
            String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

            // Run async to avoid blocking server thread
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        int context = plugin.getConfig().getInt("context_messages", 8);
                        List<DatabaseManager.StoredMessage> history = plugin.getDatabaseManager().getConversation(uuid, context);
                        // Add user's message to DB
                        plugin.getDatabaseManager().addMessage(uuid, "user", message);
                        // Build messages list for client
                        java.util.ArrayList<CerebrasClient.Message> messages = new java.util.ArrayList<>();
                        for (DatabaseManager.StoredMessage sm : history) {
                            messages.add(new CerebrasClient.Message(sm.role, sm.content));
                        }
                        messages.add(new CerebrasClient.Message("user", message));

                        String reply = plugin.getCerebrasClient().generate(messages);
                        if (reply == null) {
                            reply = "No response (check server logs or API key/endpoint).";
                        } else {
                            // store assistant message
                            plugin.getDatabaseManager().addMessage(uuid, "assistant", reply);
                        }

                        final String finalReply = reply;
                        // send message back on main thread
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage("§6[AI Assistant] §r" + finalReply);
                        });

                    } catch (Exception ex) {
                        plugin.getLogger().severe("Error during AI chat: " + ex.getMessage());
                        ex.printStackTrace();
                        Bukkit.getScheduler().runTask(plugin, () -> sendMsg(player, "An error occurred while processing your request."));
                    }
                }
            }.runTaskAsynchronously(plugin);

            return true;
        }

        if (sub.equals("view")) {
            if (!(sender instanceof Player)) {
                sendMsg(sender, "Only players can use /ai view.");
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("ai.history.view")) {
                sendMsg(player, "You don't have permission to view your AI history.");
                return true;
            }
            List<DatabaseManager.StoredMessage> history = plugin.getDatabaseManager().getConversation(player.getUniqueId(), plugin.getConfig().getInt("view_messages", 20));
            plugin.getGuiManager().openConversation(player, history);
            return true;
        }

        if (sub.equals("export")) {
            if (!(sender instanceof Player)) {
                sendMsg(sender, "Only players can use /ai export.");
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("ai.history.export")) {
                sendMsg(player, "You don't have permission to export your AI history.");
                return true;
            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        List<DatabaseManager.StoredMessage> history = plugin.getDatabaseManager().getConversation(player.getUniqueId(), plugin.getConfig().getInt("export_max_messages", 1000));
                        File exportDir = new File(plugin.getDataFolder(), "exports");
                        if (!exportDir.exists()) exportDir.mkdirs();
                        String filename = player.getUniqueId().toString() + "_" + Instant.now().getEpochSecond() + ".json";
                        File out = new File(exportDir, filename);

                        try (FileWriter fw = new FileWriter(out)) {
                            fw.write(plugin.getGson().toJson(history));
                        }

                        Bukkit.getScheduler().runTask(plugin, () -> sendMsg(player, "Exported history to: plugins/" + plugin.getName() + "/exports/" + filename));
                    } catch (Exception ex) {
                        plugin.getLogger().severe("Error exporting history: " + ex.getMessage());
                        ex.printStackTrace();
                        Bukkit.getScheduler().runTask(plugin, () -> sendMsg(player, "Failed to export history."));
                    }
                }
            }.runTaskAsynchronously(plugin);

            return true;
        }

        if (sub.equals("clear")) {
            if (!(sender instanceof Player)) {
                sendMsg(sender, "Only players can use /ai clear.");
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("ai.history.clear")) {
                sendMsg(player, "You don't have permission to clear your AI history.");
                return true;
            }

            // Clear async
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        plugin.getDatabaseManager().clearConversation(player.getUniqueId());
                        Bukkit.getScheduler().runTask(plugin, () -> sendMsg(player, "Your AI history has been cleared."));
                    } catch (Exception ex) {
                        plugin.getLogger().severe("Error clearing history: " + ex.getMessage());
                        ex.printStackTrace();
                        Bukkit.getScheduler().runTask(plugin, () -> sendMsg(player, "Failed to clear history."));
                    }
                }
            }.runTaskAsynchronously(plugin);

            return true;
        }

        sendMsg(sender, "Unknown subcommand: " + sub);
        return true;
    }
}
