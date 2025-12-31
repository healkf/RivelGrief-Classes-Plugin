package healkf.healkftwo;

import org.bukkit.*;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GorgonListener implements Listener {

    private final Healkftwo plugin;
    private final Map<UUID, Integer> shotCounts = new HashMap<>();

    // tc
    private static final double FULL_DRAW = 0.5;

    private static final int EFFECT_TICKS = 20 * 2; // 2 -3 12 секунды
    private static final int BLIND_AMPLIFIER = 0;
    private static final int STUN_SLOW_AMPLIFIER = 10;

    private static final double AOE_RADIUS = 3.0;

    public GorgonListener(Healkftwo plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerShootBow(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player shooter = (Player) e.getEntity();

        Map<UUID, String> classes = plugin.getPlayerClasses();
        if (classes == null) return;
        String clazz = classes.get(shooter.getUniqueId());
        if (clazz == null || !clazz.equalsIgnoreCase("gorgon")) return;

        if (!(e.getProjectile() instanceof Arrow)) return;
        Arrow arrow = (Arrow) e.getProjectile();

        try {
            arrow.addScoreboardTag("gorgon_owner_" + shooter.getUniqueId());
        } catch (Throwable ignored) {}

        float force = e.getForce();
        if (force >= FULL_DRAW) {
            UUID id = shooter.getUniqueId();
            int count = shotCounts.getOrDefault(id, 0) + 1;
            if (count >= 3) count = 3;
            shotCounts.put(id, count);

            if (count == 3) {
                arrow.addScoreboardTag("gorgon_third");
                shotCounts.put(id, 0);

                // 4
                shooter.getWorld().playSound(shooter.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.2f, 1.1f);
                shooter.getWorld().spawnParticle(Particle.SPELL_WITCH, shooter.getLocation().add(0, 1.5, 0), 40, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }

    @EventHandler
    public void onArrowHit(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        if (!(damager instanceof Arrow)) return;
        Arrow arrow = (Arrow) damager;

        UUID ownerUuid = null;
        for (String tag : arrow.getScoreboardTags()) {
            if (tag.startsWith("gorgon_owner_")) {
                try {
                    ownerUuid = UUID.fromString(tag.replace("gorgon_owner_", ""));
                } catch (IllegalArgumentException ignored) {}
                break;
            }
        }

        Player owner = (ownerUuid != null) ? plugin.getServer().getPlayer(ownerUuid) : null;
        if (owner == null && arrow.getShooter() instanceof Player) {
            owner = (Player) arrow.getShooter();
        }
        if (owner == null) return;

        Map<UUID, String> classes = plugin.getPlayerClasses();
        if (classes == null) return;
        String clazz = classes.get(owner.getUniqueId());
        if (clazz == null || !clazz.equalsIgnoreCase("gorgon")) return;

        boolean isThird = arrow.getScoreboardTags().contains("gorgon_third");
        double baseDamage = e.getDamage();
        Entity mainTarget = e.getEntity();

        if (mainTarget instanceof Player target) {
            if (isThird) {
                e.setDamage(baseDamage * 2.0);
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, EFFECT_TICKS, BLIND_AMPLIFIER, true, false));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, EFFECT_TICKS, STUN_SLOW_AMPLIFIER, true, false));
            }
        } else if (isThird) {
            e.setDamage(baseDamage * 2.0);
        }

        // aoe
        for (Entity near : arrow.getNearbyEntities(AOE_RADIUS, AOE_RADIUS, AOE_RADIUS)) {
            if (!(near instanceof Player p)) continue;
            if (p.equals(owner) || p.equals(mainTarget)) continue;

            double damage = isThird ? baseDamage * 2.0 : baseDamage;
            p.damage(damage, owner);

            if (isThird) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, EFFECT_TICKS, BLIND_AMPLIFIER, true, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, EFFECT_TICKS, STUN_SLOW_AMPLIFIER, true, false));
            }
        }

        // visual
        if (isThird) {
            Location loc = arrow.getLocation();
            World world = loc.getWorld();

            world.playSound(loc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.3f, 0.9f);
            world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.6f);

            world.spawnParticle(Particle.CRIT_MAGIC, loc, 50, 0.4, 0.4, 0.4, 0.1);
            world.spawnParticle(Particle.SMOKE_LARGE, loc, 20, 0.3, 0.3, 0.3, 0.05);
        }

        try { arrow.removeScoreboardTag("gorgon_third"); } catch (Throwable ignored) {}
        try { arrow.removeScoreboardTag("gorgon_owner_" + owner.getUniqueId()); } catch (Throwable ignored) {}
    }
}
