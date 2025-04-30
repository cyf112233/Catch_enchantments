package top.cxkcxkckx.Enchantments;
        
import org.jetbrains.annotations.NotNull;
import top.mrxiaom.pluginbase.BukkitPlugin;
import top.mrxiaom.pluginbase.EconomyHolder;
import top.cxkcxkckx.Enchantments.func.EnchantmentExtractor;

public class Enchantments extends BukkitPlugin {
    public static Enchantments getInstance() {
        return (Enchantments) BukkitPlugin.getInstance();
    }

    public Enchantments() {
        super(options()
                .bungee(false)
                .adventure(false)
                .database(false)
                .reconnectDatabaseWhenReloadConfig(false)
                .vaultEconomy(false)
                .scanIgnore("top.mrxiaom.example.libs")
        );
    }

    @Override
    protected void afterEnable() {
        // 注册附魔提取器监听器
        getServer().getPluginManager().registerEvents(new EnchantmentExtractor(this), this);
        getLogger().info("§a==========================================");
        getLogger().info("§aCatch_enchantments 插件已加载");
        getLogger().info("§a作者: cxkcxkckx");
        getLogger().info("§a版本: " + getDescription().getVersion());
        getLogger().info("§a==========================================");
    }
}
