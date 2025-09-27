package com.ihsannoob.aiplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class GuiManager implements Listener {

    private final AiPlugin plugin;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public GuiManager(AiPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Open a simple pageless inventory showing messages (latest shown last).
     * Clicking an item will send full message to player chat.
     */
    public void openConversation(Player player, List<DatabaseManager.StoredMessage> messages) {
        int slotsNeeded = Math.max(1, messages.size());
        int rows = (int) Math.ceil(slotsNeeded / 9.0);
        rows = Math.min(6, Math.max(1, rows));
        int size = rows * 9;
        Inventory inv = Bukkit.createInventory(null, size, "AI History: " + player.getName());

        for (int i = 0; i < messages.size() && i < size; i++) {
            DatabaseManager.StoredMessage m = messages.get(i);
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            String title = (m.role.equalsIgnoreCase("user") ? ChatColor.YELLOW : ChatColor.AQUA) + (m.role.equals("user") ? "User" : "Assistant");
            meta.setDisplayName(title);
            List<String> lore = new ArrayList<>();
            String snippet = m.content.length() > 80 ? m.content.substring(0, 77) + "..." : m.content;
            lore.add(ChatColor.GRAY + snippet);
            lore.add(ChatColor.GRAY + "At: " + dtf.format(Instant.ofEpochSecond(m.createdAt)));
            lore.add(ChatColor.GRAY + "Click to view full message in chat");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent ev) {
        if (ev.getView().getTitle() == null) return;
        if (!ev.getView().getTitle().startsWith("AI History: ")) return;
        ev.setCancelled(true); // prevent taking items
        if (!(ev.getWhoClicked() instanceof Player)) return;
        Player player = (Player) ev.getWhoClicked();
        int slot = ev.getSlot();
        // get message index from slot
        List<DatabaseManager.StoredMessage> history = plugin.getDatabaseManager().getConversation(player.getUniqueId(), player.getOpenInventory().getTopInventory().getSize());
        if (slot < 0 || slot >= history.size()) return;
        DatabaseManager.StoredMessage m = history.get(slot);
        player.sendMessage("§6[AI History] §7Role: §e" + m.role + " §7At: §e" + dtf.format(Instant.ofEpochSecond(m.createdAt)));
        // Split long message in chat lines
        String content = m.content;
        int maxLen = 256;
        for (int start = 0; start < content.length(); start += maxLen) {
            int end = Math.min(content.length(), start + maxLen);
            player.sendMessage(content.substring(start, end));
        }
    }
}
