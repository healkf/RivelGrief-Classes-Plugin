package healkf.healkftwo;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class MechanicListener implements Listener {

    private final Healkftwo plugin;

    private static final String TOOL_NAME = "§6Инженерный инструмент";
    private static final long TURRET_LIFETIME_TICKS = 40 * 20L;
    private static final long COOLDOWN_TICKS = 120 * 20L;

    // Состояния
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, ArmorStand> activeTurrets = new HashMap<>();

    public MechanicListener(Healkftwo plugin) {
        this.plugin = plugin;
        startTurretAI();
    }


    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (!(e.getDamager() instanceof Projectile)) return;

        Player victim = (Player) e.getEntity();
        Map<UUID, String> classes = plugin.getPlayerClasses();
        if (classes == null) return;
        String clazz = classes.get(victim.getUniqueId());
        if (clazz == null || !clazz.equalsIgnoreCase("mechanic")) return;


        double projectileMultiplier = 0.4;
        e.setDamage(e.getDamage() * projectileMultiplier);
    }

    private ItemStack createTool() {
        ItemStack item = new ItemStack(Material.IRON_HOE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TOOL_NAME);
            meta.setLore(Collections.singletonList("§7ПКМ — установить турель (КД 2 минуты)"));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void giveMechanicItem(Player p) {
        boolean has = false;
        for (ItemStack it : p.getInventory().getContents()) {
            if (isMechanicTool(it)) {
                has = true;
                break;
            }
        }
        if (!has) p.getInventory().addItem(createTool());
        // Дать постоянное сопротивление снарядам
        p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 1, false, false));

    }

    private boolean isMechanicTool(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && TOOL_NAME.equals(meta.getDisplayName());
    }

    //турель

    private void spawnTurret(Player owner) {
        Location loc = owner.getLocation().add(owner.getLocation().getDirection().multiply(1.5));
        loc.setY(loc.getY() - 1);

        World world = loc.getWorld();
        if (world == null) return;

        ArmorStand stand = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSmall(false);
        stand.getEquipment().setItemInMainHand(new ItemStack(Material.CROSSBOW));
        stand.setCustomName("§7[§6Турель " + owner.getName() + "§7]");
        stand.setCustomNameVisible(true);

        activeTurrets.put(owner.getUniqueId(), stand);

        world.playSound(loc, Sound.BLOCK_PISTON_EXTEND, 1.0f, 1.2f);
        world.spawnParticle(Particle.SMOKE_LARGE, loc.add(0, 1, 0), 10, 0.5, 0.5, 0.5);

        // Удаление через 40 секунд
        new BukkitRunnable() {
            @Override
            public void run() {
                if (stand.isValid()) stand.remove();
                activeTurrets.remove(owner.getUniqueId());
                owner.sendMessage("§7Твоя турель §cсамоуничтожилась.");
                world.playSound(loc, Sound.BLOCK_ANVIL_BREAK, 1f, 0.8f);
            }
        }.runTaskLater(plugin, TURRET_LIFETIME_TICKS);
    }

    private void startTurretAI() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, ArmorStand> entry : activeTurrets.entrySet()) {
                    UUID ownerId = entry.getKey();
                    ArmorStand turret = entry.getValue();
                    if (!turret.isValid()) continue;

                    Player owner = Bukkit.getPlayer(ownerId);
                    if (owner == null || !owner.isOnline()) continue;

                    Entity target = findTarget(turret, owner);
                    if (target != null) {
                        shootArrow(turret, target);
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 20L); // стреляет раз в секунду
    }

    private Entity findTarget(ArmorStand turret, Player owner) {
        double radius = 15;
        List<Entity> nearby = turret.getNearbyEntities(radius, radius, radius);

        Entity playerTarget = null;
        Entity mobTarget = null;

        for (Entity e : nearby) {
            if (e instanceof Player p) {
                if (p.equals(owner)) continue; // не стреляет в владельца
                if (p.isDead()) continue;
                playerTarget = p;
                break;
            } else if (e instanceof Monster m) {
                if (!m.isDead()) mobTarget = m;
            }
        }

        return playerTarget != null ? playerTarget : mobTarget;
    }

    private void shootArrow(ArmorStand turret, Entity target) {
        Location loc = turret.getEyeLocation().add(0, 1.2, 0);
        Vector dir = target.getLocation().add(0, 1, 0).subtract(loc).toVector().normalize();

        Arrow arrow = turret.getWorld().spawnArrow(loc, dir, 1.5f, 2f);
        arrow.setShooter(turret);
        arrow.setCritical(true);
        arrow.setDamage(4.0);

        turret.getWorld().playSound(loc, Sound.ITEM_CROSSBOW_SHOOT, 1f, 1.2f);
        turret.getWorld().spawnParticle(Particle.SMOKE_NORMAL, loc, 8, 0.2, 0.2, 0.2);
    }

    // обработка событий

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isMechanic(p)) return;
        ItemStack item = e.getItem();
        if (!isMechanicTool(item)) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        e.setCancelled(true);

        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(p.getUniqueId(), 0L);
        long cdMillis = COOLDOWN_TICKS * 50L;

        if (now - last < cdMillis) {
            long remain = (cdMillis - (now - last)) / 1000;
            p.sendMessage("§cОжидай " + remain + " сек. до следующей установки турели!");
            return;
        }

        cooldowns.put(p.getUniqueId(), now);
        spawnTurret(p);
        p.sendMessage("§aТурель установлена на 40 секунд!");
    }
    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
        ArmorStand stand = e.getRightClicked();
        if (activeTurrets.containsValue(stand)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cТы не можешь взаимодействовать с турелью!");
        }
    }
    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof ArmorStand stand && activeTurrets.containsValue(stand)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> b.getType() == Material.AIR);
        for (Entity entity : e.getEntity().getNearbyEntities(5, 5, 5)) {
            if (entity instanceof ArmorStand stand && activeTurrets.containsValue(stand)) {
                e.setCancelled(true);
                break;
            }
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        for (Entity entity : e.getBlock().getWorld().getNearbyEntities(e.getBlock().getLocation(), 5, 5, 5)) {
            if (entity instanceof ArmorStand stand && activeTurrets.containsValue(stand)) {
                e.setCancelled(true);
                break;
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack item = e.getItemDrop().getItemStack();
        if (isMechanicTool(item)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cЭтот инструмент нельзя выбрасывать!");
        }
    }

    private boolean isMechanic(Player p) {
        Map<UUID, String> classes = plugin.getPlayerClasses();
        if (classes == null) return false;
        String clazz = classes.get(p.getUniqueId());
        return clazz != null && clazz.equalsIgnoreCase("mechanic");
    }
}


