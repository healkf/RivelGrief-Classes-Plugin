package healkf.healkftwo;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.*;
import java.util.*;

public class RatKing implements Listener {

    private final Healkftwo plugin;
    private final Map<UUID, Long> ratCooldowns = new HashMap<>();
    private final Map<UUID, List<Entity>> playerRats = new HashMap<>();

    private static final int MAX_RATS = 6;
    private static final long RAT_COOLDOWN = 1000L * 60 * 5; // 5 минут

    public RatKing(Healkftwo plugin) {
        this.plugin = plugin;
    }

    // логика спавна чешуек
    @EventHandler
    public void onPlayerUseRatItem(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        String clazz = plugin.getPlayerClasses().get(p.getUniqueId());
        if (!"ratcatcher".equals(clazz)) return;

        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;
        if (!"§6Пыль Крысоловa".equals(item.getItemMeta().getDisplayName())) return;

        e.setCancelled(true);

        // проверка коулдауна
        long current = System.currentTimeMillis();
        long lastUse = ratCooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (current - lastUse < RAT_COOLDOWN) {
            long sec = (RAT_COOLDOWN - (current - lastUse)) / 1000;
            p.sendMessage("§cПредмет на перезарядке: " + sec + " секунд");
            return;
        }
        ratCooldowns.put(p.getUniqueId(), current);


        List<Silverfish> rats = new ArrayList<>();
        Location playerLoc = p.getLocation();
        Vector lookDir = playerLoc.getDirection().clone().setY(0).normalize(); // только XZ
        Vector right = lookDir.clone().crossProduct(new Vector(0, 1, 0)).normalize();

        double spacing = 1.2;
        int half = MAX_RATS / 2;

        for (int i = 0; i < MAX_RATS; i++) {
            double offset = (i - half + 0.5) * spacing;

            Location spawnLoc = playerLoc.clone()
                    .add(lookDir.clone().multiply(2.0))
                    .add(right.clone().multiply(offset));
            spawnLoc.setY(spawnLoc.getY() + 0.6 + (i * 0.01));


            Silverfish rat = (Silverfish) p.getWorld().spawnEntity(spawnLoc, EntityType.SILVERFISH);

            if (rat == null) {
                plugin.getLogger().warning("spawn returned null for i=" + i);
                continue;
            }


            if (rat.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null) {
                rat.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0); // в 2 раза, если стандарт 4
            }
            rat.setHealth(Math.min(20.0, rat.getMaxHealth()));
            rat.setAI(true);
            rat.setPersistent(true);
            rat.addScoreboardTag("rat_owner_" + p.getUniqueId());

            rats.add(rat);


            plugin.getLogger().info("spawned rat id=" + rat.getEntityId() + " at " +
                    Math.round(spawnLoc.getX()) + "," + Math.round(spawnLoc.getY()) + "," + Math.round(spawnLoc.getZ()));


            final long[] lastAttack = new long[]{0L};
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (rat.isDead() || !rat.isValid()) {
                        cancel();
                        return;
                    }


                    if (lastAttack[0] == 0L) {

                    }

                    Player owner = p;
                    if (owner == null || !owner.isOnline()) {

                        if (!rat.isDead()) rat.remove();
                        cancel();
                        return;
                    }


                    double searchRadius = 15.0;
                    Player nearest = null;
                    double nearestSq = Double.MAX_VALUE;
                    for (Entity ent : rat.getNearbyEntities(searchRadius, searchRadius, searchRadius)) {
                        if (!(ent instanceof Player candidate)) continue;
                        if (candidate.getUniqueId().equals(owner.getUniqueId())) continue;
                        double dsq = candidate.getLocation().distanceSquared(rat.getLocation());
                        if (dsq < nearestSq) {
                            nearestSq = dsq;
                            nearest = candidate;
                        }
                    }

                    if (nearest != null) {
                        double dist = Math.sqrt(nearestSq);
                        double meleeRange = 1.6;
                        long now = System.currentTimeMillis();

                        if (dist <= meleeRange && now - lastAttack[0] >= 800) {
                            lastAttack[0] = now;


                            nearest.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0), true);
                            nearest.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0), true);


                            nearest.damage(2.0, rat);


                            rat.setTarget(nearest);

                        } else {

                            rat.setTarget(nearest);
                            Vector toTarget = nearest.getLocation().toVector().subtract(rat.getLocation().toVector());
                            if (toTarget.lengthSquared() > 0.01) {
                                rat.setVelocity(toTarget.normalize().multiply(0.25));
                            }
                        }
                    } else {

                        Vector toOwner = owner.getLocation().toVector().subtract(rat.getLocation().toVector());
                        if (toOwner.lengthSquared() > 0.01) {
                            rat.setVelocity(toOwner.normalize().multiply(0.3));
                        }
                        rat.setTarget(null);
                    }
                }
            }.runTaskTimer(plugin, 2L, 10L);
        }


        playerRats.put(p.getUniqueId(), new ArrayList<>(rats));
        p.sendMessage("§aВы призвали " + rats.size() + " чешуйниц!");


    }

    // ======= Поведение: не атакуют владельца =======
    @EventHandler
    public void onRatTarget(EntityTargetEvent e) {
        if (!(e.getEntity() instanceof Silverfish)) return;

        Silverfish rat = (Silverfish) e.getEntity();
        UUID ownerUUID = getOwnerUUID(rat);
        if (ownerUUID == null) return;

        Entity target = e.getTarget();
        if (target instanceof Player targetPlayer) {
            if (targetPlayer.getUniqueId().equals(ownerUUID)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onRatDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Silverfish rat)) return;

        UUID ownerUUID = getOwnerUUID(rat);
        if (ownerUUID == null) return;

        if (e.getEntity() instanceof Player target) {
            if (target.getUniqueId().equals(ownerUUID)) {
                e.setCancelled(true);
                return;
            }

            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, false, false, false));
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0, false, false, false));

            e.setDamage(e.getDamage() * 4);
        }
    }

    private UUID getOwnerUUID(Silverfish rat) {
        for (String tag : rat.getScoreboardTags()) {
            if (tag.startsWith("rat_owner_")) {
                return UUID.fromString(tag.replace("rat_owner_", ""));
            }
        }
        return null;
    }
}
