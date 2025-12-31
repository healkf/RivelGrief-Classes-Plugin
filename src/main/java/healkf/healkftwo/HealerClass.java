package healkf.healkftwo;


import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;


public class HealerClass implements Listener {


    private final Healkftwo plugin;


    private final Map<UUID, Set<UUID>> allies = new HashMap<>();

    // кулдауны
    private final Map<UUID, Long> wandCooldowns = new HashMap<>();
    private final Map<UUID, Long> bagCooldowns = new HashMap<>();
    private final long COOLDOWN_MS = 2 * 60 * 1000L;

    // настройки
    private static final int EFFECT_TICKS = 20 * 30;
    private static final int REGEN_AMPLIFIER = 1;
    private static final int SPEED_AMPLIFIER = 0;

    public HealerClass(Healkftwo plugin) {
        this.plugin = plugin;
    }

    // iterms

    public ItemStack createWandItem() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta m = item.getItemMeta();
        if (m != null) {
            m.setDisplayName("§aЛечебная палочка");
            m.setLore(Arrays.asList("§7ПКМ — применить эффекты союзникам", "§7ЛКМ по игроку — отметить/снять союзника"));
            m.setUnbreakable(true);
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(m);
        }
        return item;
    }

    public ItemStack createBagItem() {
        ItemStack item = new ItemStack(Material.GHAST_TEAR);
        ItemMeta m = item.getItemMeta();
        if (m != null) {
            m.setDisplayName("§dЛечебная сумка");
            m.setLore(Arrays.asList("§7ПКМ — мгновенно восстановить союзникам +15 HP", "§7ЛКМ по игроку — отметить/снять союзника"));
            m.setUnbreakable(true);
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(m);
        }
        return item;
    }

    // iu

    private boolean isHealer(Player p) {
        if (p == null) return false;
        Map<UUID, String> classes = plugin.getPlayerClasses();
        if (classes == null) return false;
        String clazz = classes.get(p.getUniqueId());
        return "healer".equalsIgnoreCase(clazz) || "лекарь".equalsIgnoreCase(clazz);
    }

    private boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta m = item.getItemMeta();
        return m.hasDisplayName() && "§aЛечебная палочка".equals(m.getDisplayName());
    }

    private boolean isBag(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta m = item.getItemMeta();
        return m.hasDisplayName() && "§dЛечебная сумка".equals(m.getDisplayName());
    }

    private Set<UUID> getAllies(UUID owner) {
        return allies.computeIfAbsent(owner, k -> new HashSet<>());
    }


    @EventHandler
    public void onMarkAlly(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) return;
        Player damager = (Player) e.getDamager();

        if (!(e.getEntity() instanceof Player)) return;
        Player target = (Player) e.getEntity();

        if (!isHealer(damager)) return;

        ItemStack inHand = damager.getInventory().getItemInMainHand();
        if (!isWand(inHand) && !isBag(inHand)) {
            return; // не наш предмет
        }

        e.setCancelled(true);
        Set<UUID> set = getAllies(damager.getUniqueId());
        UUID tid = target.getUniqueId();
        if (set.contains(tid)) {
            set.remove(tid);
            damager.sendMessage("§eИгрок §f" + target.getName() + " §eудалён из союзников.");
            target.sendMessage("§cВы больше не помечены как союзник игрока §f" + damager.getName());
        } else {
            set.add(tid);
            damager.sendMessage("§aИгрок §f" + target.getName() + " §aпомечен как союзник.");
            target.sendMessage("§2Вас пометил как союзника игрок §f" + damager.getName());
        }
    }

    @EventHandler
    public void onUseItem(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        if (!isHealer(p)) return;

        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;

        if (isWand(item)) {
            long now = System.currentTimeMillis();
            long last = wandCooldowns.getOrDefault(p.getUniqueId(), 0L);
            if (now - last < COOLDOWN_MS) {
                long sec = (COOLDOWN_MS - (now - last)) / 1000;
                p.sendMessage("§cЛечебная палочка ещё на перезарядке: §f" + sec + "§c сек.");
                return;
            }
            wandCooldowns.put(p.getUniqueId(), now);

            Set<UUID> set = new HashSet<>(getAllies(p.getUniqueId()));
            set.add(p.getUniqueId());

            int applied = 0;
            for (UUID uid : new HashSet<>(set)) {
                Player ally = Bukkit.getPlayer(uid);
                if (ally == null || !ally.isOnline()) continue;
                ally.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, EFFECT_TICKS, 0, true, false));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, EFFECT_TICKS, REGEN_AMPLIFIER, true, true));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, EFFECT_TICKS, SPEED_AMPLIFIER, true, false));
                if (!ally.getUniqueId().equals(p.getUniqueId())) {
                    ally.sendMessage("§aВы получили лечение от §f" + p.getName() + "§a (палочка).");
                } else {
                    ally.sendMessage("§aВы воспользовались палочкой и получили эффекты.");
                }
                applied++;
            }

            p.sendMessage("§aПалочка применена. Исцелено: §f" + applied + " §a(включая вас). КД 2 минуты.");
            e.setCancelled(true);
            return;
        }

        if (isBag(item)) {
            long now = System.currentTimeMillis();
            long last = bagCooldowns.getOrDefault(p.getUniqueId(), 0L);
            if (now - last < COOLDOWN_MS) {
                long sec = (COOLDOWN_MS - (now - last)) / 1000;
                p.sendMessage("§cЛечебная сумка ещё на перезарядке: §f" + sec + "§c сек.");
                return;
            }
            bagCooldowns.put(p.getUniqueId(), now);

            Set<UUID> set = new HashSet<>(getAllies(p.getUniqueId()));
            set.add(p.getUniqueId());

            int healed = 0;
            for (UUID uid : new HashSet<>(set)) {
                Player ally = Bukkit.getPlayer(uid);
                if (ally == null || !ally.isOnline()) continue;

                double max = ally.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                double newHp = Math.min(max, ally.getHealth() + 15.0);
                ally.setHealth(newHp);
                if (!ally.getUniqueId().equals(p.getUniqueId())) {
                    ally.sendMessage("§aВам восстановили здоровье +15 (от §f" + p.getName() + "§a).");
                } else {
                    ally.sendMessage("§aВы использовали сумку и восстановили себе +15 HP.");
                }
                healed++;
            }

            p.sendMessage("§aСумка использована. Восстановлено: §f" + healed + " §a(включая вас). КД 2 минуты.");
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (!isHealer(p)) return;

        ItemStack item = e.getItemDrop().getItemStack();
        if (item == null || !item.hasItemMeta()) return;
        if (isWand(item) || isBag(item)) {
            e.setCancelled(true);
            p.sendMessage("§cВы не можете выбросить этот предмет!");
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHealer(p)) return;
            boolean hasWand = false, hasBag = false;
            for (ItemStack it : p.getInventory().getContents()) {
                if (isWand(it)) hasWand = true;
                if (isBag(it)) hasBag = true;
            }
            if (!hasWand) p.getInventory().addItem(createWandItem());
            if (!hasBag) p.getInventory().addItem(createBagItem());
        }, 5L);
    }


    public void giveHealerItems(Player p) {
        if (p == null) return;
        p.getInventory().addItem(createWandItem());
        p.getInventory().addItem(createBagItem());
        p.sendMessage("§aВам выданы предметы класса Лекарь.");
    }

    public void clearPlayerData(UUID player) {
        allies.remove(player);
        wandCooldowns.remove(player);
        bagCooldowns.remove(player);
    }
}

