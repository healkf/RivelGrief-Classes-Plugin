package healkf.healkftwo;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SnowmanClass implements Listener {

    private final Healkftwo plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final int COOLDOWN_SECONDS = 240; // 4 минуты

    public SnowmanClass(Healkftwo plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSnowballUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;

        // Проверяем, что игрок — снежный человек
        if (!"snowman".equals(plugin.getPlayerClasses().get(p.getUniqueId()))) return;

        Action action = e.getAction();
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item != null && item.getItemMeta() != null &&
                    "§bСнежный шар".equals(item.getItemMeta().getDisplayName())) {

                // Проверка кулдауна
                long now = System.currentTimeMillis();
                long lastUse = cooldowns.getOrDefault(p.getUniqueId(), 0L);
                if (now - lastUse < COOLDOWN_SECONDS * 1000L) {
                    long remaining = (COOLDOWN_SECONDS * 1000L - (now - lastUse)) / 1000L;
                    p.sendMessage("§cСнежный ком ещё не готов! Осталось: " + remaining + " сек.");
                    e.setCancelled(true);
                    return;
                }
                cooldowns.put(p.getUniqueId(), now);

                e.setCancelled(true); // Отменяем стандартное действие


                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 4, true, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 120, 4, true, false)); // Resistance V
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 120, 9, true, false)); // Slowness X
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 9, true, false)); // Weakness X
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 120, 0, true, false));

                p.sendMessage("§eВы закатались в снежный ком и восстанавливаете силы...");

                new BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        if (ticks >= 120 || !p.isOnline()) {
                            this.cancel();
                            return;
                        }
                        p.getWorld().spawnParticle(
                                Particle.SNOW_SHOVEL,
                                p.getLocation().add(0, 1, 0),
                                30, 0.5, 0.5, 0.5
                        );
                        ticks += 2;
                    }
                }.runTaskTimer(plugin, 0L, 2L);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (p.isOnline()) {
                            double maxHealth = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                            p.setHealth(maxHealth);
                            p.sendMessage("§aВы восстановились и готовы к бою!");
                        }
                    }
                }.runTaskLater(plugin, 120L);
            }
        }
    }

    @EventHandler
    public void onSnowmanDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (!"snowman".equals(plugin.getPlayerClasses().get(p.getUniqueId()))) return;

        ItemStack item = e.getItemDrop().getItemStack();
        if (item != null && item.hasItemMeta() &&
                "§bСнежный шар".equals(item.getItemMeta().getDisplayName())) {
            e.setCancelled(true);
            p.sendMessage("§cВы не можете выбросить Снежный шар!");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        cooldowns.remove(e.getPlayer().getUniqueId());
    }
}

