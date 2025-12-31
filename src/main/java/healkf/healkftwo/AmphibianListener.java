package healkf.healkftwo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class AmphibianListener implements Listener {

    private final Healkftwo plugin;


    private static final double ELECTRIC_RADIUS = 7.0;

    private static final double ELECTRIC_DAMAGE = 1.0;

    private static final long ELECTRIC_INTERVAL_TICKS = 20L;


    private static final double WATER_DAMAGE_MULT = 1.25;


    private static final String TOGGLE_NAME = "§bЭлектрорегулятор";


    private final Map<UUID, Boolean> fieldOn = new HashMap<>();


    private final Map<UUID, Set<UUID>> allies = new HashMap<>();


    public AmphibianListener(Healkftwo plugin) {
        this.plugin = plugin;
        startPeriodicTask();
    }



    private ItemStack createToggleItem() {
        ItemStack item = new ItemStack(Material.TRIDENT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TOGGLE_NAME);
            meta.setLore(Arrays.asList("§7ПКМ — включить/выключить электрическое поле",
                    "§7ЛКМ по игроку — пометить/снять союзника (без урона)"));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void giveAmphibianItem(Player p) {
        if (p == null) return;
        boolean has = false;
        for (ItemStack it : p.getInventory().getContents()) {
            if (isToggleItem(it)) { has = true; break; }
        }
        if (!has) {
            p.getInventory().addItem(createToggleItem());
        }

        fieldOn.put(p.getUniqueId(), false);
    }

    private boolean isToggleItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta m = item.getItemMeta();
        return m.hasDisplayName() && TOGGLE_NAME.equals(m.getDisplayName());
    }



    private void startPeriodicTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        Map<UUID, String> classes = plugin.getPlayerClasses();
                        if (classes == null) continue;
                        String clazz = classes.get(p.getUniqueId());
                        if (clazz == null || !clazz.equalsIgnoreCase("amphibian")) continue;


                        handleWaterBuffs(p);


                        Boolean on = fieldOn.getOrDefault(p.getUniqueId(), false);
                        if (on && p.isInWater()) {
                            spawnFieldVisuals(p);
                            applyElectricDamage(p);
                        }
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Amphibian periodic task error: " + t.getMessage());
                    t.printStackTrace();
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void handleWaterBuffs(Player p) {

        if (p.isInWater()) {

            try {
                int maxAir = p.getMaximumAir();
                p.setRemainingAir(maxAir);
            } catch (Throwable ignored) {

            }


            p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 40, 0, true, false, true));


            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false, true));
        } else {
            // p.removePotionEffect(PotionEffectType.WATER_BREATHING);
            // p.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    private void spawnFieldVisuals(Player amphibian) {
        Location center = amphibian.getLocation();
        amphibian.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0,1,0), 20, 1.0, 1.0, 1.0);
        amphibian.getWorld().playSound(center, Sound.BLOCK_CHAIN_BREAK, 0.6f, 1.2f);
    }

    private void applyElectricDamage(Player amphibian) {
        Location center = amphibian.getLocation();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(amphibian)) continue;
            if (!target.isInWater()) continue; // target должен быть в воде
            if (!target.getWorld().equals(center.getWorld())) continue;

            double distSq = target.getLocation().distanceSquared(center);
            if (distSq > ELECTRIC_RADIUS * ELECTRIC_RADIUS) continue;

            // не бить союзников
            Set<UUID> set = allies.getOrDefault(amphibian.getUniqueId(), Collections.emptySet());
            if (set.contains(target.getUniqueId())) continue;

            try {
                target.damage(ELECTRIC_DAMAGE, amphibian);
            } catch (Throwable t) {
                target.damage(ELECTRIC_DAMAGE);
            }

            // визуал у цели
            target.getWorld().spawnParticle(Particle.CRIT_MAGIC, target.getLocation().add(0,1,0), 8, 0.3,0.3,0.3);
        }
    }



    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Map<UUID, String> classes = plugin.getPlayerClasses();
        if (classes == null) return;
        String clazz = classes.get(p.getUniqueId());
        if (clazz == null || !clazz.equalsIgnoreCase("amphibian")) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> giveAmphibianItem(p), 5L);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        Map<UUID, String> classes = plugin.getPlayerClasses();
        if (classes == null) return;
        String clazz = classes.get(p.getUniqueId());
        if (clazz == null || !clazz.equalsIgnoreCase("amphibian")) return;

        ItemStack item = e.getItemDrop().getItemStack();
        if (isToggleItem(item)) {
            e.setCancelled(true);
            p.sendMessage("§cВы не можете выбросить этот предмет!");
        }
    }



    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Map<UUID, String> classes = plugin.getPlayerClasses();
        if (classes == null) return;
        String clazz = classes.get(p.getUniqueId());
        if (clazz == null || !clazz.equalsIgnoreCase("amphibian")) return;

        ItemStack item = e.getItem();
        if (!isToggleItem(item)) return;

        // ПКМ переключение поля
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            UUID id = p.getUniqueId();
            boolean on = fieldOn.getOrDefault(id, false);
            fieldOn.put(id, !on);
            p.sendMessage(!on ? "§aЭлектрическое поле включено." : "§cЭлектрическое поле выключено.");
            p.getWorld().playSound(p.getLocation(), !on ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, !on ? 1.6f : 0.8f);
        }
    }

    @EventHandler
    public void onMarkAlly(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) return;
        Player damager = (Player) e.getDamager();

        Map<UUID, String> classes = plugin.getPlayerClasses();
        if (classes == null) return;
        String clazz = classes.get(damager.getUniqueId());
        if (clazz == null || !clazz.equalsIgnoreCase("amphibian")) return;

        if (!(e.getEntity() instanceof Player)) return;
        Player target = (Player) e.getEntity();

        ItemStack inHand = damager.getInventory().getItemInMainHand();
        if (!isToggleItem(inHand)) return;

        // переключаем пометку: отменяем сам урон
        e.setCancelled(true);
        Set<UUID> set = allies.computeIfAbsent(damager.getUniqueId(), k -> new HashSet<>());
        UUID tid = target.getUniqueId();
        if (set.contains(tid)) {
            set.remove(tid);
            damager.sendMessage("§eИгрок §f" + target.getName() + " §eвыпал из списка союзников.");
            target.sendMessage("§cВас сняли как союзника у §f" + damager.getName());
        } else {
            set.add(tid);
            damager.sendMessage("§aИгрок §f" + target.getName() + " §aпомечен как союзник.");
            target.sendMessage("§2Вас пометил как союзника игрок §f" + damager.getName());
        }
    }



    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) return;
        Player damager = (Player) e.getDamager();

        Map<UUID, String> classes = plugin.getPlayerClasses();
        if (classes == null) return;
        String clazz = classes.get(damager.getUniqueId());
        if (clazz == null || !clazz.equalsIgnoreCase("amphibian")) return;


        if (!damager.isInWater()) return;

        e.setDamage(e.getDamage() * WATER_DAMAGE_MULT);
    }
}
//package healkf.healkftwo;
//
//import org.bukkit.Bukkit;
//import org.bukkit.Location;
//import org.bukkit.Material;
//import org.bukkit.Particle;
//import org.bukkit.Sound;
//import org.bukkit.entity.Player;
//import org.bukkit.event.EventHandler;
//import org.bukkit.event.Listener;
//import org.bukkit.event.block.Action;
//import org.bukkit.event.entity.EntityDamageByEntityEvent;
//import org.bukkit.event.player.*;
//import org.bukkit.inventory.ItemFlag;
//import org.bukkit.inventory.ItemStack;
//import org.bukkit.inventory.meta.ItemMeta;
//import org.bukkit.potion.PotionEffect;
//import org.bukkit.potion.PotionEffectType;
//import org.bukkit.scheduler.BukkitRunnable;
//import org.bukkit.util.Vector;
//
//import java.util.*;
//
//public class AmphibianListener implements Listener {
//
//    private final Healkftwo plugin;
//
//    private static final double ELECTRIC_RADIUS = 7.0;
//    private static final double ELECTRIC_DAMAGE = 1.0;
//    private static final long ELECTRIC_INTERVAL_TICKS = 20L;
//
//    private static final double WATER_DAMAGE_MULT = 1.25;
//
//    private static final String TOGGLE_NAME = "§bЭлектрорегулятор";
//
//    private final Map<UUID, Boolean> fieldOn = new HashMap<>();
//
//    private final Map<UUID, Set<UUID>> allies = new HashMap<>();
//
//    public AmphibianListener(Healkftwo plugin) {
//        this.plugin = plugin;
//        startElectricTask();
//    }
//
//
//    private ItemStack createToggleItem() {
//        ItemStack item = new ItemStack(Material.TRIDENT);
//        ItemMeta meta = item.getItemMeta();
//        if (meta != null) {
//            meta.setDisplayName(TOGGLE_NAME);
//            meta.setLore(Arrays.asList("§7ПКМ — включить/выключить электрическое поле",
//                    "§7ЛКМ по игроку — пометить/снять союзника (без урона)"));
//            meta.setUnbreakable(true);
//            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
//            item.setItemMeta(meta);
//        }
//        return item;
//    }
//
//    public void giveAmphibianItem(Player p) {
//        if (p == null) return;
//        boolean has = false;
//        for (ItemStack it : p.getInventory().getContents()) {
//            if (isToggleItem(it)) { has = true; break; }
//        }
//        if (!has) {
//            p.getInventory().addItem(createToggleItem());
//        }
//        // по умолчанию поле выключено
//        fieldOn.put(p.getUniqueId(), false);
//    }
//
//    private boolean isToggleItem(ItemStack item) {
//        if (item == null || !item.hasItemMeta()) return false;
//        ItemMeta m = item.getItemMeta();
//        return m.hasDisplayName() && TOGGLE_NAME.equals(m.getDisplayName());
//    }
//
//
//    private void startElectricTask() {
//        new BukkitRunnable() {
//            @Override
//            public void run() {
//                try {
//                    for (Player amphibian : Bukkit.getOnlinePlayers()) {
//                        Map<UUID, String> classes = plugin.getPlayerClasses();
//                        if (classes == null) continue;
//                        String clazz = classes.get(amphibian.getUniqueId());
//                        if (clazz == null || !clazz.equalsIgnoreCase("amphibian")) continue;
//
//                        Boolean on = fieldOn.getOrDefault(amphibian.getUniqueId(), false);
//                        if (!on) continue;
//
//                        // амфибия должна быть в воде, чтобы поле ударяло
//                        if (!amphibian.isInWater()) continue;
//
//                        Location center = amphibian.getLocation();
//
//                        // частицы / звук для визуала поля
//                        amphibian.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0,1,0), 20, 1.0, 1.0, 1.0);
//                        amphibian.getWorld().playSound(center, Sound.BLOCK_CHAIN_BREAK, 0.6f, 1.2f);
//
//                        // проходим по игрокам рядом
//                        for (Player target : Bukkit.getOnlinePlayers()) {
//                            if (target.equals(amphibian)) continue;
//                            if (!target.isInWater()) continue; // target должен быть в воде (по условию)
//                            if (!target.getWorld().equals(center.getWorld())) continue;
//
//                            double distSq = target.getLocation().distanceSquared(center);
//                            if (distSq > ELECTRIC_RADIUS * ELECTRIC_RADIUS) continue;
//
//                            // не бить союзников
//                            Set<UUID> set = allies.getOrDefault(amphibian.getUniqueId(), Collections.emptySet());
//                            if (set.contains(target.getUniqueId())) continue;
//
//                            // Нанесём урон как от амфибии (если online)
//                            try {
//                                target.damage(ELECTRIC_DAMAGE, amphibian);
//                            } catch (Throwable t) {
//                                target.damage(ELECTRIC_DAMAGE);
//                            }
//
//                            // визуал у цели
//                            target.getWorld().spawnParticle(Particle.CRIT_MAGIC, target.getLocation().add(0,1,0), 8, 0.3,0.3,0.3);
//                        }
//                    }
//                } catch (Throwable t) {
//                    plugin.getLogger().warning("Amphibian electric task error: " + t.getMessage());
//                    t.printStackTrace();
//                }
//            }
//        }.runTaskTimer(plugin, ELECTRIC_INTERVAL_TICKS, ELECTRIC_INTERVAL_TICKS);
//    }
//
//
//    @EventHandler
//    public void onPlayerRespawn(PlayerRespawnEvent e) {
//        Player p = e.getPlayer();
//        Map<UUID, String> classes = plugin.getPlayerClasses();
//        if (classes == null) return;
//        String clazz = classes.get(p.getUniqueId());
//        if (clazz == null || !clazz.equalsIgnoreCase("amphibian")) return;
//
//        // дать предмет через пару тиков, чтобы инвентарь успел загрузиться
//        Bukkit.getScheduler().runTaskLater(plugin, () -> giveAmphibianItem(p), 5L);
//    }
//
//    @EventHandler
//    public void onDrop(PlayerDropItemEvent e) {
//        Player p = e.getPlayer();
//        Map<UUID, String> classes = plugin.getPlayerClasses();
//        if (classes == null) return;
//        String clazz = classes.get(p.getUniqueId());
//        if (clazz == null || !clazz.equalsIgnoreCase("amphibian")) return;
//
//        ItemStack item = e.getItemDrop().getItemStack();
//        if (isToggleItem(item)) {
//            e.setCancelled(true);
//            p.sendMessage("§cВы не можете выбросить этот предмет!");
//        }
//    }
//
//
//    @EventHandler
//    public void onPlayerInteract(PlayerInteractEvent e) {
//        Player p = e.getPlayer();
//        Map<UUID, String> classes = plugin.getPlayerClasses();
//        if (classes == null) return;
//        String clazz = classes.get(p.getUniqueId());
//        if (clazz == null || !clazz.equalsIgnoreCase("amphibian")) return;
//
//        ItemStack item = e.getItem();
//        if (!isToggleItem(item)) return;
//
//        // ПКМ — переключение поля
//        Action a = e.getAction();
//        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
//            e.setCancelled(true);
//            UUID id = p.getUniqueId();
//            boolean on = fieldOn.getOrDefault(id, false);
//            fieldOn.put(id, !on);
//            p.sendMessage(!on ? "§aЭлектрическое поле включено." : "§cЭлектрическое поле выключено.");
//            // небольшой звуковой сигнал
//            p.getWorld().playSound(p.getLocation(), !on ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, !on ? 1.6f : 0.8f);
//        }
//    }
//
//    @EventHandler
//    public void onMarkAlly(EntityDamageByEntityEvent e) {
//        if (!(e.getDamager() instanceof Player)) return;
//        Player damager = (Player) e.getDamager();
//
//        Map<UUID, String> classes = plugin.getPlayerClasses();
//        if (classes == null) return;
//        String clazz = classes.get(damager.getUniqueId());
//        if (clazz == null || !clazz.equalsIgnoreCase("amphibian")) return;
//
//        if (!(e.getEntity() instanceof Player)) return;
//        Player target = (Player) e.getEntity();
//
//        ItemStack inHand = damager.getInventory().getItemInMainHand();
//        if (!isToggleItem(inHand)) return;
//
//        e.setCancelled(true);
//        Set<UUID> set = allies.computeIfAbsent(damager.getUniqueId(), k -> new HashSet<>());
//        UUID tid = target.getUniqueId();
//        if (set.contains(tid)) {
//            set.remove(tid);
//            damager.sendMessage("§eИгрок §f" + target.getName() + " §eвыпал из списка союзников.");
//            target.sendMessage("§cВас сняли как союзника у §f" + damager.getName());
//        } else {
//            set.add(tid);
//            damager.sendMessage("§aИгрок §f" + target.getName() + " §aпомечен как союзник.");
//            target.sendMessage("§2Вас пометил как союзника игрок §f" + damager.getName());
//        }
//    }
//
//
//    @EventHandler
//    public void onDamage(EntityDamageByEntityEvent e) {
//        if (!(e.getDamager() instanceof Player)) return;
//        Player damager = (Player) e.getDamager();
//
//        Map<UUID, String> classes = plugin.getPlayerClasses();
//        if (classes == null) return;
//        String clazz = classes.get(damager.getUniqueId());
//        if (clazz == null || !clazz.equalsIgnoreCase("amphibian")) return;
//
//        if (!damager.isInWater()) return;
//
//        e.setDamage(e.getDamage() * WATER_DAMAGE_MULT);
//    }
//
//}
