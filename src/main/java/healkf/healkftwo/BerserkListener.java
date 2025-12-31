package healkf.healkftwo;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class BerserkListener implements Listener {

    private final Healkftwo plugin;

    // cooldowns
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 60 * 1000L; // 1 минута

    // активные баффы
    private final Map<UUID, Long> activeRageEnd = new HashMap<>();
    private final Map<UUID, BukkitRunnable> rageDamageRunnables = new HashMap<>();
    private final Map<UUID, BukkitRunnable> rageVisualRunnables = new HashMap<>();

    private final Map<UUID, AttributeModifier> currentSpeedModifier = new HashMap<>();

    // параметры
    private static final double RAGE_DAMAGE_MULT = 1.5; // +50% урона
    private static final int RAGE_DURATION_SECONDS = 5;
    private static final double RAGE_SELF_DAMAGE_PER_SECOND = 4.0;
    private static final double ATTACK_SPEED_EXTRA_AT_ZERO_HP = 6.0;
    private static final long ATTACK_SPEED_UPDATE_TICKS = 10L;

    // имя предмета
    private static final String TOTEM_NAME = "§cКровавый тотем";

    public BerserkListener(Healkftwo plugin) {
        this.plugin = plugin;
        startAttackSpeedUpdater();
    }


    private ItemStack createTotemItem() {
        ItemStack it = new ItemStack(Material.RED_DYE);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(TOTEM_NAME);
            m.setLore(Arrays.asList(
                    "§7ПКМ — включить Кровавую ярость: +50% урона на 5s",
                    "§7(Но вы теряете §c2 сердца/сек§7 в течение действия).",
                    "§7КД: 1 минута. Нельзя выбросить."
            ));
            m.setUnbreakable(true);
            m.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(m);
        }
        return it;
    }

    public void giveBerserkItem(Player p) {
        if (p == null) return;
        boolean has = false;
        for (ItemStack it : p.getInventory().getContents()) {
            if (isTotem(it)) { has = true; break; }
        }
        if (!has) p.getInventory().addItem(createTotemItem());
    }

    private boolean isTotem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta m = item.getItemMeta();
        return m.hasDisplayName() && TOTEM_NAME.equals(m.getDisplayName());
    }


    private void startAttackSpeedUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        Map<UUID, String> classes = plugin.getPlayerClasses();
                        if (classes == null) continue;
                        String clazz = classes.get(p.getUniqueId());
                        if (clazz == null || !clazz.equalsIgnoreCase("berserk")) continue;

                        AttributeInstance attr = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
                        if (attr == null) continue;

                        double maxHealth = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                        double curHealth = Math.max(0.0, p.getHealth());
                        double frac = (maxHealth <= 0.0) ? 1.0 : (curHealth / maxHealth);


                        double extra = (1.0 - frac) * ATTACK_SPEED_EXTRA_AT_ZERO_HP;


                        AttributeModifier old = currentSpeedModifier.get(p.getUniqueId());
                        if (old != null) {
                            try { attr.removeModifier(old); } catch (Throwable ignored) {}
                        }


                        AttributeModifier mod = new AttributeModifier(UUID.nameUUIDFromBytes(("berserk_speed_" + p.getUniqueId()).getBytes()),
                                "berserk_speed", extra, AttributeModifier.Operation.ADD_NUMBER);
                        try {
                            attr.addModifier(mod);
                            currentSpeedModifier.put(p.getUniqueId(), mod);
                        } catch (Throwable t) {
                            plugin.getLogger().warning("Berserk: failed to set attack speed modifier for " + p.getName() + ": " + t.getMessage());
                        }
                    }


                    for (UUID id : new ArrayList<>(currentSpeedModifier.keySet())) {
                        Player pl = Bukkit.getPlayer(id);
                        if (pl == null) {
                            currentSpeedModifier.remove(id);
                            continue;
                        }
                        Map<UUID, String> classes = plugin.getPlayerClasses();
                        if (classes == null) continue;
                        String clazz = classes.get(id);
                        if (clazz == null || !clazz.equalsIgnoreCase("berserk")) {
                            AttributeInstance attr = pl.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
                            AttributeModifier mod = currentSpeedModifier.remove(id);
                            if (attr != null && mod != null) {
                                try { attr.removeModifier(mod); } catch (Throwable ignored) {}
                            }
                        }
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Berserk attack-speed updater error: " + t.getMessage());
                    t.printStackTrace();
                }
            }
        }.runTaskTimer(plugin, 2L, ATTACK_SPEED_UPDATE_TICKS);
    }



    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Map<UUID, String> classes = plugin.getPlayerClasses();
        if (classes == null) return;
        String clazz = classes.get(p.getUniqueId());
        if (clazz == null || !clazz.equalsIgnoreCase("berserk")) return;

        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack it = e.getItem();
        if (!isTotem(it)) return;
        e.setCancelled(true);

        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < COOLDOWN_MS) {
            long left = (COOLDOWN_MS - (now - last)) / 1000;
            p.sendMessage("§cКровавый тотем в перезарядке: " + left + " сек.");
            return;
        }


        cooldowns.put(p.getUniqueId(), now);
        long endAt = now + RAGE_DURATION_SECONDS * 1000L;
        activeRageEnd.put(p.getUniqueId(), endAt);


        BukkitRunnable oldDamage = rageDamageRunnables.remove(p.getUniqueId());
        if (oldDamage != null) oldDamage.cancel();
        BukkitRunnable oldVisual = rageVisualRunnables.remove(p.getUniqueId());
        if (oldVisual != null) oldVisual.cancel();


        BukkitRunnable rb = new BukkitRunnable() {
            int ticksLeft = RAGE_DURATION_SECONDS;
            @Override
            public void run() {
                if (ticksLeft <= 0 || !p.isOnline() || p.isDead()) {
                    rageDamageRunnables.remove(p.getUniqueId());
                    cancel();
                    return;
                }
                // наносим самоповреждение (2 сердца = 4.0)
                try {
                    p.damage(RAGE_SELF_DAMAGE_PER_SECOND);
                } catch (Throwable t) {
                    double newHp = Math.max(0.0, p.getHealth() - RAGE_SELF_DAMAGE_PER_SECOND);
                    p.setHealth(newHp);
                }
                ticksLeft--;
            }
        };
        rb.runTaskTimer(plugin, 0L, 20L); // раз в секунду
        rageDamageRunnables.put(p.getUniqueId(), rb);

        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0,1,0), 50, 0.6, 0.8, 0.6);
        p.getWorld().spawnParticle(Particle.SMOKE_LARGE, p.getLocation().add(0,0.5,0), 20, 0.8, 0.8, 0.8);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);

        BukkitRunnable visual = new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = RAGE_DURATION_SECONDS * 20 / 4 + 2;
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || ticks > maxTicks) {
                    rageVisualRunnables.remove(p.getUniqueId());
                    cancel();
                    return;
                }
                Location loc = p.getLocation().add(0, 0.9, 0);
                p.getWorld().spawnParticle(Particle.FLAME, loc, 12, 0.5, 0.5, 0.5);
                p.getWorld().spawnParticle(Particle.SMOKE_NORMAL, loc, 6, 0.6, 0.4, 0.6);
                p.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.15f, 1.3f);
                ticks++;
            }
        };
        visual.runTaskTimer(plugin, 0L, 4L);
        rageVisualRunnables.put(p.getUniqueId(), visual);

        p.sendMessage("§cКровавая ярость активирована! +50% урона на " + RAGE_DURATION_SECONDS + " сек. (КД 1 мин)");
    }


    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) return;
        Player attacker = (Player) e.getDamager();

        Map<UUID, String> classes = plugin.getPlayerClasses();
        if (classes == null) return;
        String clazz = classes.get(attacker.getUniqueId());
        if (clazz == null || !clazz.equalsIgnoreCase("berserk")) return;

        // если в activeRage и ещё не истёк — увеличиваем урон
        Long end = activeRageEnd.get(attacker.getUniqueId());
        if (end != null && System.currentTimeMillis() < end) {
            e.setDamage(e.getDamage() * RAGE_DAMAGE_MULT);
        }
    }


    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        Map<UUID, String> classes = plugin.getPlayerClasses();
        if (classes == null) return;
        String clazz = classes.get(p.getUniqueId());
        if (clazz == null || !clazz.equalsIgnoreCase("berserk")) return;

        ItemStack item = e.getItemDrop().getItemStack();
        if (isTotem(item)) {
            e.setCancelled(true);
            p.sendMessage("§cВы не можете выбросить Кровавый тотем!");
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Map<UUID, String> classes = plugin.getPlayerClasses();
        if (classes == null) return;
        String clazz = classes.get(p.getUniqueId());
        if (clazz == null || !clazz.equalsIgnoreCase("berserk")) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> giveBerserkItem(p), 5L);
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        BukkitRunnable r = rageDamageRunnables.remove(id);
        if (r != null) r.cancel();
        BukkitRunnable v = rageVisualRunnables.remove(id);
        if (v != null) v.cancel();

        AttributeModifier mod = currentSpeedModifier.remove(id);
        Player p = e.getPlayer();
        if (mod != null && p != null) {
            AttributeInstance attr = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            if (attr != null) {
                try { attr.removeModifier(mod); } catch (Throwable ignored) {}
            }
        }
        activeRageEnd.remove(id);
        cooldowns.remove(id);
    }
}

