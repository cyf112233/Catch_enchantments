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
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import top.cxkcxkckx.Enchantments.Enchantments;
import org.bukkit.ChatColor;
import java.util.Iterator;
import org.bukkit.NamespacedKey;
import java.lang.reflect.Field;
import java.util.Arrays;

public class EnchantmentExtractor implements Listener {
    
    private final Enchantments plugin;
    private static final int LEVEL_PER_ENCHANT = 6; // 每个附魔消耗的经验等级
    private Set<NamespacedKey> blacklistedEnchantments;
    private Set<Enchantment> registeredEnchantments;
    
    public EnchantmentExtractor(Enchantments plugin) {
        this.plugin = plugin;
        loadRegisteredEnchantments();
        loadBlacklistedEnchantments();
    }

    private void loadRegisteredEnchantments() {
        registeredEnchantments = new HashSet<>();
        try {
            // 获取所有已注册的附魔
            Field byKeyField = Enchantment.class.getDeclaredField("byKey");
            byKeyField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<NamespacedKey, Enchantment> byKey = (Map<NamespacedKey, Enchantment>) byKeyField.get(null);
            registeredEnchantments.addAll(byKey.values());
            
            // 获取所有已注册的附魔（通过名称）
            Field byNameField = Enchantment.class.getDeclaredField("byName");
            byNameField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Enchantment> byName = (Map<String, Enchantment>) byNameField.get(null);
            registeredEnchantments.addAll(byName.values());
        } catch (Exception e) {
            plugin.getLogger().warning("无法加载已注册的附魔: " + e.getMessage());
            // 如果反射失败，使用默认的附魔列表
            registeredEnchantments.addAll(Arrays.asList(Enchantment.values()));
        }
    }

    private void loadBlacklistedEnchantments() {
        blacklistedEnchantments = new HashSet<>();
        if (!plugin.getConfig().getBoolean("enchantment-extractor.allow-all-enchantments", true)) {
            List<String> blacklist = plugin.getConfig().getStringList("enchantment-extractor.blacklisted-enchantments");
            for (String enchantName : blacklist) {
                try {
                    // 尝试通过命名空间获取附魔
                    NamespacedKey key = NamespacedKey.fromString(enchantName);
                    if (key != null) {
                        // 验证附魔是否存在
                        Enchantment enchant = Enchantment.getByKey(key);
                        if (enchant != null) {
                            blacklistedEnchantments.add(key);
                        } else {
                            plugin.getLogger().warning("找不到附魔: " + enchantName);
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("无效的附魔命名空间: " + enchantName);
                }
            }
        }
    }

    private boolean isEnchantmentAllowed(Enchantment enchant) {
        if (plugin.getConfig().getBoolean("enchantment-extractor.allow-all-enchantments", true)) {
            return true;
        }
        
        // 获取附魔的命名空间
        NamespacedKey key = enchant.getKey();
        return !blacklistedEnchantments.contains(key);
    }

    private Map<Enchantment, Integer> filterAllowedEnchantments(Map<Enchantment, Integer> enchants) {
        Map<Enchantment, Integer> filtered = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            if (isEnchantmentAllowed(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    private int calculateRequiredLevel(ItemStack item) {
        if (item == null) return 0;
        
        int enchantCount;
        if (item.getType() == Material.ENCHANTED_BOOK) {
            // 如果是附魔书，只计算第一个附魔
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            if (meta != null && !meta.getStoredEnchants().isEmpty()) {
                Map.Entry<Enchantment, Integer> firstEnchant = meta.getStoredEnchants().entrySet().iterator().next();
                if (isEnchantmentAllowed(firstEnchant.getKey())) {
                    enchantCount = 1;
                } else {
                    enchantCount = 0;
                }
            } else {
                enchantCount = 0;
            }
        } else {
            // 如果是普通物品，计算所有允许的附魔
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                Map<Enchantment, Integer> filteredEnchants = filterAllowedEnchantments(meta.getEnchants());
                enchantCount = filteredEnchants.size();
            } else {
                enchantCount = 0;
            }
        }
        
        return enchantCount * LEVEL_PER_ENCHANT;
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

        // 获取玩家
        if (!(event.getView().getPlayer() instanceof Player)) return;
        Player player = (Player) event.getView().getPlayer();

        // 计算所需经验等级
        int requiredLevel;
        String configLevel = plugin.getConfig().getString("enchantment-extractor.required-level", "auto");
        if ("auto".equalsIgnoreCase(configLevel)) {
            requiredLevel = calculateRequiredLevel(secondItem);
        } else {
            try {
                requiredLevel = Integer.parseInt(configLevel);
            } catch (NumberFormatException e) {
                requiredLevel = 30; // 默认值
            }
        }

        // 如果没有可提取的附魔，取消操作
        if (requiredLevel == 0) {
            event.setResult(null);
            return;
        }

        // 检查玩家经验等级（创造模式无视限制）
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
        
        // 处理不同类型的物品
        if (secondItem.getType() == Material.ENCHANTED_BOOK) {
            // 如果是附魔书，只提取第一个允许的附魔
            Map<Enchantment, Integer> enchants = getEnchantments(secondItem);
            Map<Enchantment, Integer> filteredEnchants = filterAllowedEnchantments(enchants);
            if (filteredEnchants.isEmpty()) {
                event.setResult(null);
                return;
            }
            // 如果附魔书只有一个附魔，不允许提取
            if (enchants.size() == 1) {
                event.setResult(null);
                return;
            }
            // 只取第一个允许的附魔
            Map.Entry<Enchantment, Integer> firstEnchant = filteredEnchants.entrySet().iterator().next();
            meta.addStoredEnchant(firstEnchant.getKey(), firstEnchant.getValue(), true);
        } else {
            // 如果是普通物品，复制所有允许的附魔
            Map<Enchantment, Integer> enchants = getEnchantments(secondItem);
            Map<Enchantment, Integer> filteredEnchants = filterAllowedEnchantments(enchants);
            if (filteredEnchants.isEmpty()) {
                event.setResult(null);
                return;
            }
            for (Map.Entry<Enchantment, Integer> entry : filteredEnchants.entrySet()) {
                meta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            }
        }
        
        // 如果没有可提取的附魔，取消操作
        if (meta.getStoredEnchants().isEmpty()) {
            event.setResult(null);
            return;
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
        
        // 获取玩家
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        // 计算所需经验等级
        int requiredLevel;
        String configLevel = plugin.getConfig().getString("enchantment-extractor.required-level", "auto");
        if ("auto".equalsIgnoreCase(configLevel)) {
            requiredLevel = calculateRequiredLevel(secondItem);
        } else {
            try {
                requiredLevel = Integer.parseInt(configLevel);
            } catch (NumberFormatException e) {
                requiredLevel = 30; // 默认值
            }
        }
        
        // 检查玩家经验等级（创造模式无视限制）
        if (!player.getGameMode().equals(org.bukkit.GameMode.CREATIVE) && player.getLevel() < requiredLevel) {
            event.setCancelled(true);
            return;
        }
        
        // 处理不同类型的物品
        if (secondItem.getType() == Material.ENCHANTED_BOOK) {
            // 如果是附魔书，只移除第一个允许的附魔
            Map<Enchantment, Integer> enchants = getEnchantments(secondItem);
            Map<Enchantment, Integer> filteredEnchants = filterAllowedEnchantments(enchants);
            if (filteredEnchants.isEmpty()) {
                event.setCancelled(true);
                return;
            }
            // 如果附魔书只有一个附魔，不允许提取
            if (enchants.size() == 1) {
                event.setCancelled(true);
                return;
            }
            // 只移除第一个允许的附魔
            Map.Entry<Enchantment, Integer> firstEnchant = filteredEnchants.entrySet().iterator().next();
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) secondItem.getItemMeta();
            meta.removeStoredEnchant(firstEnchant.getKey());
            secondItem.setItemMeta(meta);
            // 无论是否还有附魔，都保留附魔书
            inventory.setItem(1, secondItem);
            
            // 设置物品（消耗一本书）
            if (firstItem.getAmount() > 1) {
                firstItem.setAmount(firstItem.getAmount() - 1);
                inventory.setItem(0, firstItem);
            } else {
                inventory.setItem(0, null);
            }
        } else {
            // 如果是普通物品，移除所有允许的附魔
            ItemMeta meta = secondItem.getItemMeta();
            Map<Enchantment, Integer> enchants = meta.getEnchants();
            boolean removed = false;
            for (Enchantment enchant : enchants.keySet()) {
                if (isEnchantmentAllowed(enchant)) {
                    meta.removeEnchant(enchant);
                    removed = true;
                }
            }
            if (removed) {
                secondItem.setItemMeta(meta);
                // 对于装备，无论是否还有附魔都保留
                inventory.setItem(1, secondItem);
                
                // 设置物品（消耗一本书）
                if (firstItem.getAmount() > 1) {
                    firstItem.setAmount(firstItem.getAmount() - 1);
                    inventory.setItem(0, firstItem);
                } else {
                    inventory.setItem(0, null);
                }
            } else {
                // 如果没有可移除的附魔，取消操作
                event.setCancelled(true);
                return;
            }
        }
        
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
            String message = plugin.getConfig().getString("enchantment-extractor.success-message", "&a成功提取附魔！消耗了{level}级经验。");
            message = message.replace("{level}", String.valueOf(requiredLevel));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
        
        // 取消事件，防止循环触发
        event.setCancelled(true);
    }

    private boolean hasEnchantments(ItemStack item) {
        if (item == null) return false;
        if (item.getType() == Material.ENCHANTED_BOOK) {
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            if (meta != null && !meta.getStoredEnchants().isEmpty()) {
                // 检查是否有允许的附魔
                for (Enchantment enchant : meta.getStoredEnchants().keySet()) {
                    if (isEnchantmentAllowed(enchant)) {
                        return true;
                    }
                }
            }
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && !meta.getEnchants().isEmpty()) {
            // 检查是否有允许的附魔
            for (Enchantment enchant : meta.getEnchants().keySet()) {
                if (isEnchantmentAllowed(enchant)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Map<Enchantment, Integer> getEnchantments(ItemStack item) {
        if (item == null) return new HashMap<>();
        if (item.getType() == Material.ENCHANTED_BOOK) {
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            return meta != null ? meta.getStoredEnchants() : new HashMap<>();
        }
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