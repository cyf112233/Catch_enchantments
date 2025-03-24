package top.cxkcxkckx.Enchantments.func;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Map;
import java.util.HashMap;
import top.cxkcxkckx.Enchantments.Enchantments;
import org.bukkit.ChatColor;

public class EnchantmentExtractor implements Listener {
    
    private final Enchantments plugin;
    
    public EnchantmentExtractor(Enchantments plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack firstItem = inventory.getItem(0); // 第一个槽位
        ItemStack secondItem = inventory.getItem(1); // 第二个槽位

        // 检查物品是否为空
        if (firstItem == null || secondItem == null) return;

        // 检查第一个物品是否为书
        if (firstItem.getType() != Material.BOOK) return;

        // 检查第二个物品是否有附魔
        if (!hasEnchantments(secondItem)) return;

        // 获取玩家
        if (!(event.getView().getPlayer() instanceof Player)) return;
        Player player = (Player) event.getView().getPlayer();

        // 检查玩家经验等级（创造模式无视限制）
        int requiredLevel = plugin.getConfig().getInt("enchantment-extractor.required-level", 30);
        if (!player.getGameMode().equals(org.bukkit.GameMode.CREATIVE) && player.getLevel() < requiredLevel) {
            // 设置红色提示文字
            inventory.setRepairCost(requiredLevel);
            inventory.setItem(2, createErrorItem(requiredLevel));
            event.setResult(null);
            return;
        }

        // 创建新的附魔书
        ItemStack result = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) result.getItemMeta();
        
        // 复制所有附魔
        Map<Enchantment, Integer> enchants = getEnchantments(secondItem);
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            meta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
        }
        
        result.setItemMeta(meta);
        
        // 设置结果
        event.setResult(result);
        
        // 设置经验消耗
        inventory.setRepairCost(requiredLevel);
    }

    @EventHandler
    public void onAnvilComplete(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory)) return;
        AnvilInventory inventory = (AnvilInventory) event.getInventory();
        if (event.getSlotType() != org.bukkit.event.inventory.InventoryType.SlotType.RESULT) return;
        
        ItemStack firstItem = inventory.getItem(0);
        ItemStack secondItem = inventory.getItem(1);
        ItemStack resultItem = inventory.getItem(2); // 获取结果栏的物品
        
        if (firstItem == null || secondItem == null) return;
        if (firstItem.getType() != Material.BOOK) return;
        if (!hasEnchantments(secondItem)) return;
        
        // 获取玩家
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        // 检查玩家经验等级（创造模式无视限制）
        int requiredLevel = plugin.getConfig().getInt("enchantment-extractor.required-level", 30);
        if (!player.getGameMode().equals(org.bukkit.GameMode.CREATIVE) && player.getLevel() < requiredLevel) {
            event.setCancelled(true);
            return;
        }
        
        // 移除原物品的附魔
        ItemStack cleanItem = secondItem.clone();
        ItemMeta meta = cleanItem.getItemMeta();
        for (Enchantment enchant : meta.getEnchants().keySet()) {
            meta.removeEnchant(enchant);
        }
        cleanItem.setItemMeta(meta);
        
        // 设置物品
        inventory.setItem(0, null); // 移除第一个槽位的书
        inventory.setItem(1, cleanItem);
        
        // 处理结果栏的附魔书
        if (resultItem != null && resultItem.getType() == Material.ENCHANTED_BOOK) {
            // 检查玩家背包是否有空间
            if (player.getInventory().firstEmpty() == -1) {
                // 背包已满，将附魔书掉落在地上
                player.getWorld().dropItem(player.getLocation(), resultItem);
                // 发送提示消息
                player.sendMessage(ChatColor.YELLOW + "你的背包已满，附魔书已掉落在地上！");
            } else {
                // 背包有空间，正常添加到背包
                player.getInventory().addItem(resultItem);
            }
        }
        
        // 扣除经验（创造模式不扣除）
        if (!player.getGameMode().equals(org.bukkit.GameMode.CREATIVE)) {
            player.setLevel(player.getLevel() - requiredLevel);
        }
        
        // 发送成功消息
        if (plugin.getConfig().getBoolean("enchantment-extractor.show-success-message", true)) {
            String message = plugin.getConfig().getString("enchantment-extractor.success-message", "&a成功提取附魔！消耗了30级经验。");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    private boolean hasEnchantments(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && !meta.getEnchants().isEmpty();
    }

    private Map<Enchantment, Integer> getEnchantments(ItemStack item) {
        if (item == null) return new HashMap<>();
        ItemMeta meta = item.getItemMeta();
        return meta != null ? meta.getEnchants() : new HashMap<>();
    }

    private ItemStack createErrorItem(int requiredLevel) {
        ItemStack errorItem = new ItemStack(Material.BARRIER);
        ItemMeta meta = errorItem.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "需要 " + requiredLevel + " 级经验");
        errorItem.setItemMeta(meta);
        return errorItem;
    }
} 