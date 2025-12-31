package healkf.healkftwo;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WerewolfListener implements Listener {

    private final Healkftwo plugin;

    // Кровотечение
    private final Map<UUID, Long> bleedEndTime = new HashMap<>();
    private final Map<UUID, UUID> bleedSource = new HashMap<>();
    private final Map<UUID, Double> bleedDamage = new HashMap<>();

    public WerewolfListener(Healkftwo plugin) {
        this.plugin = plugin;

        // Таск для нанесения урона кровотечения каждую секунду
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (UUID victimId : bleedEndTime.keySet().toArray(new UUID[0])) {
                if (now > bleedEndTime.get(victimId)) {
                    bleedEndTime.remove(victimId);
                    bleedSource.remove(victimId);
                    bleedDamage.remove(victimId);
                    continue;
                }

                Player victim = Bukkit.getPlayer(victimId);
                Player source = bleedSource.containsKey(victimId) ? Bukkit.getPlayer(bleedSource.get(victimId)) : null;
                if (victim == null || victim.isDead()) {
                    bleedEndTime.remove(victimId);
                    bleedSource.remove(victimId);
                    bleedDamage.remove(victimId);
                    continue;
                }

                double dmg = bleedDamage.getOrDefault(victimId, 0.0);
                if (source != null && source.isOnline()) {
                    victim.damage(dmg, source);
                } else {
                    victim.damage(dmg);
                }
            }
        }, 20L, 20L);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                String clazz = plugin.getPlayerClasses().get(p.getUniqueId());
                if (clazz == null || !clazz.equalsIgnoreCase("werewolf")) continue;

                World world = p.getWorld();
                if (Werewolf.isNight(world.getTime())) {
                    p.addPotionEffect(new PotionEffect(
                            PotionEffectType.SPEED,
                            Werewolf.SPEED_DURATION_TICKS,
                            Werewolf.SPEED_AMPLIFIER,
                            true, false, true
                    ));
                } else {
                    if (p.hasPotionEffect(PotionEffectType.SPEED)) {
                        p.removePotionEffect(PotionEffectType.SPEED);
                    }
                }
            }
        }, 20L, Werewolf.SPEED_UPDATE_PERIOD_TICKS);
    }

    @EventHandler
    public void onWerewolfDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        String clazz = plugin.getPlayerClasses().get(attacker.getUniqueId());
        if (clazz == null || !clazz.equalsIgnoreCase("werewolf")) return;

        // ночь
        if (!Werewolf.isNight(attacker.getWorld().getTime())) return;

        // Ночной бонус к урону
        e.setDamage(e.getDamage() * Werewolf.DAMAGE_MULT_NIGHT);

        // Кровотечение
        double dmgPerSecond = e.getDamage() * Werewolf.BLEED_PERCENT_PER_SECOND;
        UUID vid = victim.getUniqueId();

        bleedEndTime.put(vid, System.currentTimeMillis() + Werewolf.BLEED_SECONDS * 1000L);
        bleedSource.put(vid, attacker.getUniqueId());
        bleedDamage.put(vid, dmgPerSecond);

        attacker.sendMessage("§cВы нанесли кровотечение!");
        victim.sendMessage("§4На вас наложено кровотечение на " + Werewolf.BLEED_SECONDS + " секунд!");
    }
}



