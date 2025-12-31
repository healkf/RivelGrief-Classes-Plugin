package healkf.healkftwo;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class Healkftwo extends JavaPlugin implements Listener {

    private HealerClass healerClass;
    private BerserkListener berserkListener;
    private MechanicListener mechanicListener;
    private AmphibianListener amphibianListener;

    // мины унабомбера
    private Map<Location, UUID> bomberMines = new HashMap<>();
    private void loadClasses() {
        if (!getConfig().isConfigurationSection("classes")) return;
        for (String key : getConfig().getConfigurationSection("classes").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String clazz = getConfig().getString("classes." + key);
                if (clazz != null) {
                    playerClasses.put(uuid, clazz);
                }
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Bad UUID in config classes: " + key + " — пропускаю");
            }
        }
    }

    private void saveClassForPlayer(UUID player, String clazz) {
        if (player == null || clazz == null) return;
        getConfig().set("classes." + player.toString(), clazz);
        saveConfig();
    }

    private final int MAX_MINES = 10;
    private Map<UUID, Integer> playerMineCount = new HashMap<>();

    private Map<UUID, String> playerClasses = new HashMap<>();

    private Map<UUID, Long> ratCooldowns = new HashMap<>();
    private Map<UUID, List<Entity>> playerRats = new HashMap<>(); // чешуйницы игрока

    @Override
    public void onEnable() {
        mechanicListener = new MechanicListener(this);
        getServer().getPluginManager().registerEvents(mechanicListener, this);
        berserkListener = new BerserkListener(this);
        amphibianListener = new AmphibianListener(this);
        healerClass = new HealerClass(this);

        getServer().getPluginManager().registerEvents(berserkListener, this);
        getServer().getPluginManager().registerEvents(amphibianListener, this);
        getServer().getPluginManager().registerEvents(healerClass, this);

        getServer().getPluginManager().registerEvents(new RatKing(this), this);
        getServer().getPluginManager().registerEvents(new GorgonListener(this), this);
        getServer().getPluginManager().registerEvents(new WerewolfListener(this), this);
        getServer().getPluginManager().registerEvents(new SnowmanClass(this), this);

        // главный класс тоже слушает некоторые события
        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("healkftwo plugin loaded!");
        loadClasses();
    }

    @Override
    public void onDisable() {
        saveClasses();
    }

    private void saveClasses() {
        for (Map.Entry<UUID, String> entry : playerClasses.entrySet()) {
            getConfig().set("classes." + entry.getKey().toString(), entry.getValue());
        }
        saveConfig();
    }

    public Map<UUID, String> getPlayerClasses() {
        return playerClasses;
    }

    private ItemStack createMineItem() {
        ItemStack item = new ItemStack(Material.TNT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§4Мина Бомбера");
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
    private ItemStack createMechanicItem() {
        ItemStack item = new ItemStack(Material.REDSTONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Турельный модуль");
            meta.setLore(Arrays.asList("§7ПКМ: поставить турель (КД 2 мин)", "§7Нельзя выбрасывать"));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }


    private ItemStack createRatDustItem() {
        ItemStack dust = new ItemStack(Material.BONE_MEAL);
        ItemMeta meta = dust.getItemMeta();
        meta.setDisplayName("§6Пыль Крысоловa");
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        dust.setItemMeta(meta);
        return dust;
    }

    private ItemStack createSnowballItem() {
        ItemStack item = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bСнежный шар");
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setLore(Arrays.asList(
                "§7При использовании на 6 секунд даёт",
                "§7снежному человеку неподвижность,",
                "§7сопротивление урону и регенерацию."
        ));
        item.setItemMeta(meta);
        return item;
    }


    private final List<String> archerLore = Arrays.asList("§7Ловкий стрелок, мастер дальнего боя.", "§7Удары луком наносят на §a25% больше урона§7.");
    private final List<String> swordsmanLore = Arrays.asList("§7Отважный воин, искусный с мечом в бою.", "§7Удары мечом наносят на §c25% больше урона§7.");
    private final List<String> alchemistLore = Arrays.asList("§7Гениальный алхимик, контролирующий силу зелий.", "§7Эффект всех зелий удваивается на §d+1 уровень§7.");
    private final List<String> healerLore = Arrays.asList("§7Лучший медик.", "§7Имеет два предмета. Лечебная сумка хилит всех союзников и себя +15 хп.", "§7Лечебная палочка дает спешку, регенерацию.");
    private final List<String> lancerLore = Arrays.asList("§7Воин на лошади, стремительный и опасный.", "§7Урон мечом увеличен при атаке с лошади §b+25%§7.");
    private final List<String> bomberLore = Arrays.asList("§7Скрытный мастер взрывов, ставящий ловушки.", "§7Можно ставить до 10 мин, взрывы наносят §48 урона§7.");
    private final List<String> ratLore = Arrays.asList("§7Адепт канализации. умеет дрессировать мышей.", "§7При активации предмета спавнит §46 чешуйниц§7.");
    private final List<String> wolfLore = Arrays.asList("§7Превращается в настоящего волка ночью", "§7Каждую ночь атакует на §4+30% урона сильнее§7, получает §4скорость 3§7", "§7при атаках накладывает на врага эффект §4кровотечения§7.");
    private final List<String> gorgonLore = Arrays.asList("§7Каждая третья стрела Горгоны оглушает цель на §42§7 секунды", "§7ослепляет и наносит двойной урон. Только для истинных охотников.");
    private final List<String> snowmanLore = Arrays.asList("§7Снежный человек, владыка снега.", "§7Способность §4снежный ком§7, на 6 секунд получает", "§7неподвижность, сопротивление к любым атакам и регенерацию.");
    private final List<String> amphibianLore = Arrays.asList("§7Амфибия, хорошо ориентируется в воде.", "§7Способность §4электрическое поле§7. Делает воду непригодной для жизни,", "§7атакует игроков в воде на расстоянии 7 блоков от амфибии.", "§7Класс имеет способность дышать под водой, а также +25% урона в воде");
    private final List<String> berserkLore = Arrays.asList("§7Берсерк — ярость и разрушение.", "§7Чем меньше HP — тем чаще удары.", "§7Предмет: §cКровавый тотем§7 — +50% урона на 5 сек, но вы теряете 2 ♥/сек (КД 1 мин).");
    private final List<String> mechanicLore = Arrays.asList("§7Механик, мастер техники и электроники.", "§7При атаке стрелами механика, снижает полученный урон на 60%", "§7Предмет: §cТурель§7 — стреляет в ближайшего игрока. (КД 2 мин).");

    private ItemStack createClassItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void unlockClassForPlayer(UUID playerUuid, String clazz) {
        if (playerUuid == null || clazz == null) return;
        getConfig().set("unlocked." + playerUuid.toString() + "." + clazz, true);
        saveConfig();
    }


    private void openClassMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "Выбор класса");

        inv.setItem(2, createClassItem(Material.BOW, "§aЛучник", archerLore));
        inv.setItem(4, createClassItem(Material.IRON_SWORD, "§cМечник", swordsmanLore));
        inv.setItem(6, createClassItem(Material.POTION, "§dАлхимик", alchemistLore));
        inv.setItem(8, createClassItem(Material.TRIDENT, "§bКопьеносец", lancerLore));
        inv.setItem(5, createClassItem(Material.BONE, "§6Крысолов", ratLore));

        boolean unlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".bomber", false);
        if (unlocked) inv.setItem(7, createClassItem(Material.TNT, "§4Бомбер", bomberLore));
        else inv.setItem(7, createClassItem(Material.BARRIER, "§4Бомбер", Collections.singletonList("§cЗакрыт")));

        boolean ratUnlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".ratcatcher", false);
        if (ratUnlocked) inv.setItem(5, createClassItem(Material.BONE, "§6Крысолов", ratLore));
        else inv.setItem(5, createClassItem(Material.BARRIER, "§6Крысолов", Collections.singletonList("§cЗакрыт")));

        boolean wolfUnlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".werewolf", false);
        if (wolfUnlocked) inv.setItem(3, createClassItem(Material.COAL, "§4Оборотень", wolfLore));
        else inv.setItem(3, createClassItem(Material.BARRIER, "§4Оборотень", Collections.singletonList("§cЗакрыт")));

        boolean healerUnlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".healer", false);
        if (healerUnlocked) inv.setItem(1, createClassItem(Material.COAL, "§4Лекарь", healerLore));
        else inv.setItem(1, createClassItem(Material.BARRIER, "§4Лекарь", Collections.singletonList("§cЗакрыт")));

        boolean snowmanUnlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".snowman", false);
        if (snowmanUnlocked) inv.setItem(16, createClassItem(Material.SNOWBALL, "§4Снежный человек", snowmanLore));
        else inv.setItem(16, createClassItem(Material.BARRIER, "§4Cнежный человек", Collections.singletonList("§cЗакрыт")));

        boolean gorgonUnlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".gorgon", false);
        if (gorgonUnlocked) inv.setItem(0, createClassItem(Material.ENDER_EYE, "§4Горгона", gorgonLore));
        else inv.setItem(0, createClassItem(Material.BARRIER, "§4Горгона", Collections.singletonList("§cЗакрыт")));

        boolean amphibianUnlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".amphibian", false);
        if (amphibianUnlocked) inv.setItem(13, createClassItem(Material.ENDER_EYE, "§bАмфибия", amphibianLore));
        else inv.setItem(13, createClassItem(Material.BARRIER, "§4Амфибия", Collections.singletonList("§cЗакрыт")));

        boolean berserkUnlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".berserk", false);
        if (berserkUnlocked) inv.setItem(14, createClassItem(Material.RED_DYE, "§bБерсерк", berserkLore));
        else inv.setItem(14, createClassItem(Material.BARRIER, "§4Берсерк", Collections.singletonList("§cЗакрыт")));

        boolean mechanicUnlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".mechanic", false);
        if (mechanicUnlocked) inv.setItem(15, createClassItem(Material.REDSTONE, "§6Механик", Arrays.asList("§7Мастер механизмов.", "§7Умеет ставить турели.")));
        else inv.setItem(15, createClassItem(Material.BARRIER, "§6Механик", Collections.singletonList("§cЗакрыт")));


        p.openInventory(inv);
    }

    private static final Set<String> CLASS_ITEM_NAMES = new HashSet<>(Arrays.asList(
            "§4Мина Бомбера",
            "§6Пыль Крысоловa",
            "§bСнежный шар",
            "§aЛечебная палочка",
            "§dЛечебная сумка",
            "§bЭлектрорегулятор",
            "§cКровавый тотем",
            "§6Пыль Крысоловa", // на всякий
            "§6Снежный ком",
            "§6Турельный модуль"

    ));

    private void removeClassItemsFromInventory(Player p) {
        if (p == null) return;
        ItemStack[] contents = p.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || !it.hasItemMeta()) continue;
            ItemMeta meta = it.getItemMeta();
            if (meta.hasDisplayName() && CLASS_ITEM_NAMES.contains(meta.getDisplayName())) {
                p.getInventory().setItem(i, null);
                changed = true;
            }
        }
        if (changed) p.updateInventory();
    }

    private void cleanupOldClassData(UUID playerUuid, String oldClass) {
        if (oldClass == null) return;
        // очистка специфичного состояния
        if (oldClass.equalsIgnoreCase("healer") && healerClass != null) {
            healerClass.clearPlayerData(playerUuid);
        }

    }


    private void assignClassToPlayer(Player p, String clazz, double maxHealth) {
        if (p == null) return;

        String old = playerClasses.get(p.getUniqueId());
        cleanupOldClassData(p.getUniqueId(), old);
        removeClassItemsFromInventory(p);

        // map
        playerClasses.put(p.getUniqueId(), clazz);

        // c
        getConfig().set("classes." + p.getUniqueId().toString(), clazz);
        saveConfig();

        if (p.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
        } else {
            p.setMaxHealth(maxHealth);
        }
        p.setHealth(Math.min(maxHealth, p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));

        switch (clazz.toLowerCase()) {
            case "bomber" -> p.getInventory().addItem(createMineItem());
            case "ratcatcher" -> p.getInventory().addItem(createRatDustItem());
            case "snowman" -> p.getInventory().addItem(createSnowballItem());
            case "healer" -> { if (healerClass != null) healerClass.giveHealerItems(p); }
            case "amphibian" -> { if (amphibianListener != null) amphibianListener.giveAmphibianItem(p); }
            case "berserk" -> { if (berserkListener != null) berserkListener.giveBerserkItem(p); }
            case "mechanic" -> { if (mechanicListener != null) mechanicListener.giveMechanicItem(p); }

        }
    }


    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!playerClasses.containsKey(p.getUniqueId())) {
            openClassMenu(p);
        } else {
            String clazz = playerClasses.get(p.getUniqueId());
            if (clazz != null) {
                assignClassItemsIfMissing(p, clazz);
            }
        }
    }


    private void assignClassItemsIfMissing(Player p, String clazz) {
        if (clazz == null) return;
        switch (clazz.toLowerCase()) {
            case "bomber" -> {
                boolean has = hasItemWithName(p, "§4Мина Бомбера");
                if (!has) p.getInventory().addItem(createMineItem());
            }
            case "ratcatcher" -> {
                boolean has = hasItemWithName(p, "§6Пыль Крысоловa");
                if (!has) p.getInventory().addItem(createRatDustItem());
            }
            case "snowman" -> {
                boolean has = hasItemWithName(p, "§bСнежный шар");
                if (!has) p.getInventory().addItem(createSnowballItem());
            }
            case "healer" -> {
                if (healerClass != null) healerClass.giveHealerItems(p);
            }
            case "amphibian" -> {
                if (amphibianListener != null) amphibianListener.giveAmphibianItem(p);
            }
            case "berserk" -> {
                if (berserkListener != null) berserkListener.giveBerserkItem(p);
            }
            case "mechanic" -> {
                if (mechanicListener != null) mechanicListener.giveMechanicItem(p);
            }

        }
    }

    private boolean hasItemWithName(Player p, String name) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || !it.hasItemMeta()) continue;
            if (name.equals(it.getItemMeta().getDisplayName())) return true;
        }
        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!"Выбор класса".equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;

        Player p = (Player) e.getWhoClicked();
        String name = e.getCurrentItem().getItemMeta().getDisplayName();

        if (name.equals("§aЛучник")) {
            assignClassToPlayer(p, "archer", 24.0);
        } else if (name.equals("§cМечник")) {
            assignClassToPlayer(p, "swordsman", 20.0);
        } else if (name.equals("§dАлхимик")) {
            assignClassToPlayer(p, "alchemist", 16.0);
        } else if (name.equals("§bКопьеносец")) {
            assignClassToPlayer(p, "lancer", 20.0);
        } else if (name.equals("§4Бомбер")) {
            boolean unlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".bomber", false);
            if (!unlocked) { p.sendMessage("§cЭтот класс закрыт для вас!"); return; }
            assignClassToPlayer(p, "bomber", 20.0);
        } else if (name.equals("§6Крысолов")) {
            boolean unlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".ratcatcher", false);
            if (!unlocked) { p.sendMessage("§cЭтот класс закрыт для вас!"); return; }
            assignClassToPlayer(p, "ratcatcher", 20.0);
        } else if (name.equals("§4Оборотень")) {
            boolean unlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".werewolf", false);
            if (!unlocked) { p.sendMessage("§cЭтот класс закрыт для вас!"); return; }
            assignClassToPlayer(p, "werewolf", 20.0);
        } else if (name.equals("§4Снежный человек")) {
            boolean unlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".snowman", false);
            if (!unlocked) { p.sendMessage("§cЭтот класс закрыт для вас!"); return; }
            assignClassToPlayer(p, "snowman", 24.0);
        } else if (name.equals("§4Лекарь")) {
            boolean unlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".healer", false);
            if (!unlocked) { p.sendMessage("§cЭтот класс закрыт для вас!"); return; }
            assignClassToPlayer(p, "healer", 22.0);
        } else if (name.equals("§4Горгона")) {
            boolean unlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".gorgon", false);
            if (!unlocked) { p.sendMessage("§cЭтот класс закрыт для вас!"); return; }
            assignClassToPlayer(p, "gorgon", 18.0);
        } else if (name.equals("§bАмфибия")) {
            boolean unlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".amphibian", false);
            if (!unlocked) { p.sendMessage("§cЭтот класс закрыт для вас!"); return; }
            assignClassToPlayer(p, "amphibian", 20.0);
            p.sendMessage("§aВы получили предмет Амфибии!");
        } else if (name.equals("§bБерсерк")) {
            boolean unlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".berserk", false);
            if (!unlocked) { p.sendMessage("§cЭтот класс закрыт для вас!"); return; }
            assignClassToPlayer(p, "berserk", 26.0);
            if (berserkListener != null) berserkListener.giveBerserkItem(p);
            p.sendMessage("§aВы получили предмет Берсерка!");
        } else if (name.equals("§6Механик")) {
            boolean unlocked = getConfig().getBoolean("unlocked." + p.getUniqueId() + ".mechanic", false);
            if (!unlocked) { p.sendMessage("§cЭтот класс закрыт для вас!"); return; }
            assignClassToPlayer(p, "mechanic", 24.0);
            if (mechanicListener != null) {
                mechanicListener.giveMechanicItem(p);
                p.sendMessage("§aВы получили предмет Механика!");
            }
        }else {
            return;
        }

        p.sendMessage("§eВы выбрали класс: " + name);
        p.closeInventory();
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) return;
        Player p = (Player) e.getDamager();
        String clazz = playerClasses.get(p.getUniqueId());
        if (clazz == null) return;

        if (clazz.equals("archer") &&
                p.getInventory().getItemInMainHand().getType() == Material.BOW) {
            e.setDamage(e.getDamage() * 1.25);
        } else if (clazz.equals("swordsman") &&
                p.getInventory().getItemInMainHand().getType().name().contains("SWORD")) {
            e.setDamage(e.getDamage() * 1.2);
        } else if (clazz.equals("lancer") &&
                p.isInsideVehicle() &&
                p.getInventory().getItemInMainHand().getType().name().contains("SWORD")) {
            e.setDamage(e.getDamage() * 1.3);
        }
    }

    private Map<UUID, List<PotionEffectType>> potionTracking = new HashMap<>();

    @EventHandler
    public void onPotionDrink(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        String clazz = playerClasses.get(p.getUniqueId());
        if (clazz == null || !clazz.equals("alchemist")) return;

        List<PotionEffectType> before = new ArrayList<>();
        for (PotionEffect effect : p.getActivePotionEffects()) {
            before.add(effect.getType());
        }
        potionTracking.put(p.getUniqueId(), before);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            List<PotionEffectType> oldEffects = potionTracking.getOrDefault(p.getUniqueId(), new ArrayList<>());
            for (PotionEffect effect : p.getActivePotionEffects()) {
                if (!oldEffects.contains(effect.getType())) {
                    int newAmplifier = Math.min(effect.getAmplifier() + 1, 9); // максимум уровня 10
                    int newDuration = effect.getDuration() * 2; // удваиваем время

                    p.addPotionEffect(new PotionEffect(
                            effect.getType(),
                            newDuration,
                            newAmplifier,
                            true,
                            true,
                            true
                    ));
                }
            }
            potionTracking.remove(p.getUniqueId());
        }, 1L);
    }

    @EventHandler
    public void onPlayerUseMine(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        String clazz = playerClasses.get(p.getUniqueId());
        if (!"bomber".equals(clazz)) return;

        if (e.getItem() == null || !e.getItem().hasItemMeta()) return;
        if (!e.getItem().getItemMeta().getDisplayName().equals("§4Мина Бомбера")) return;

        e.setCancelled(true);
        Block block = e.getClickedBlock();
        if (block == null) return;

        int count = playerMineCount.getOrDefault(p.getUniqueId(), 0);
        if (count >= MAX_MINES) {
            p.sendMessage("§cВы не можете поставить больше " + MAX_MINES + " мин!");
            return;
        }

        Location loc = block.getLocation().add(0.5, 0.25, 0.5); // 0.25 блока выше низа блока
        ArmorStand mine = (ArmorStand) p.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        mine.setVisible(false);
        mine.setGravity(false);
        mine.setInvulnerable(true);
        mine.setMarker(true); // маленький hitbox
        mine.addScoreboardTag("bomber_mine");
        mine.addScoreboardTag("owner_" + p.getUniqueId().toString());

        bomberMines.put(loc, p.getUniqueId());
        playerMineCount.put(p.getUniqueId(), count + 1);

        p.sendMessage("§aМина установлена! Текущие мины: " + (count + 1));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (e.getTo() == null) return;

        Location loc = e.getTo().clone();

        for (Map.Entry<Location, UUID> entry : new HashMap<>(bomberMines).entrySet()) {
            Location mineLoc = entry.getKey();
            UUID owner = entry.getValue();

            double dx = Math.abs(loc.getX() - mineLoc.getX());
            double dz = Math.abs(loc.getZ() - mineLoc.getZ());
            double dy = loc.getY() - mineLoc.getY(); // высота игрока относительно мины

            if (dx < 0.5 && dz < 0.5 && dy >= 0 && dy <= 2.0) {
                if (p.getUniqueId().equals(owner)) continue;

                p.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, mineLoc, 1);
                p.getWorld().playSound(mineLoc, Sound.ENTITY_GENERIC_EXPLODE, 1, 1);

                if (!p.getUniqueId().equals(owner)) {
                    p.damage(8.0); // 4 сердца
                }

                for (Entity ent : mineLoc.getWorld().getNearbyEntities(mineLoc, 0.5, 1.0, 0.5)) {
                    if (ent instanceof ArmorStand) {
                        ArmorStand as = (ArmorStand) ent;
                        if (as.getScoreboardTags().contains("bomber_mine")) {
                            as.remove();
                        }
                    }
                }

                int count = playerMineCount.getOrDefault(owner, 1);
                playerMineCount.put(owner, Math.max(0, count - 1));
                bomberMines.remove(entry.getKey());
            }
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        String clazz = playerClasses.get(p.getUniqueId());
        if (clazz == null) return;

        ItemStack item = e.getItemDrop().getItemStack();
        if (item == null || item.getItemMeta() == null) return;
        String name = item.getItemMeta().getDisplayName();

        if ("bomber".equals(clazz) && "§4Мина Бомбера".equals(name)) {
            e.setCancelled(true);
            p.sendMessage("§cВы не можете выбросить этот предмет!");
        } else if ("ratcatcher".equals(clazz) && "§6Пыль Крысоловa".equals(name)) {
            e.setCancelled(true);
            p.sendMessage("§cВы не можете выбросить этот предмет!");
        } else if ("snowman".equals(clazz) && ("§bСнежный шар".equals(name) || "§6Снежный ком".equals(name))) {
            e.setCancelled(true);
            p.sendMessage("§cВы не можете выбросить этот предмет!");
        } else if ("amphibian".equals(clazz) && "§bЭлектрорегулятор".equals(name)) {
            e.setCancelled(true);
            p.sendMessage("§cВы не можете выбросить этот предмет!");
        } else if ("healer".equals(clazz) && ("§aЛечебная палочка".equals(name) || "§dЛечебная сумка".equals(name))) {
            e.setCancelled(true);
            p.sendMessage("§cВы не можете выбросить этот предмет!");
        } else if ("berserk".equals(clazz) && "§cКровавый тотем".equals(name)) {
            e.setCancelled(true);
            p.sendMessage("§cВы не можете выбросить этот предмет!");
        } else if ("mechanic".equals(clazz) && "§6Турельный модуль".equals(name)) {
        e.setCancelled(true);
        p.sendMessage("§cВы не можете выбросить этот предмет!");
    }

}

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        String clazz = playerClasses.get(p.getUniqueId());
        if (clazz == null) return;

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if ("bomber".equals(clazz)) {
                p.getInventory().addItem(createMineItem());
            } else if ("ratcatcher".equals(clazz)) {
                p.getInventory().addItem(createRatDustItem());
            } else if ("snowman".equals(clazz)) {
                p.getInventory().addItem(createSnowballItem());
            } else if ("amphibian".equals(clazz)) {
                if (amphibianListener != null) amphibianListener.giveAmphibianItem(p);
            } else if ("healer".equals(clazz)) {
                if (healerClass != null) healerClass.giveHealerItems(p);
            } else if ("berserk".equals(clazz)) {
                if (berserkListener != null) berserkListener.giveBerserkItem(p);
            }
              else if ("mechanic".equals(clazz)) {
                if (mechanicListener != null) mechanicListener.giveMechanicItem(p);}



    }, 5L);
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("classmenu")) {
            if (args.length == 0) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cТолько игрок может открыть своё меню классов.");
                    return true;
                }
                Player p = (Player) sender;
                openClassMenu(p);
                p.sendMessage("§eОткрыто меню выбора класса.");
                return true;
            }
            if (args.length == 1) {
                if (!sender.hasPermission("healkftwo.admin") && !sender.isOp()) {
                    sender.sendMessage("§cУ вас нет прав открыть меню другого игрока.");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) { sender.sendMessage("§cИгрок не найден."); return true; }
                openClassMenu(target);
                sender.sendMessage("§aОткрыл меню класса для " + target.getName());
                target.sendMessage("§eАдминистратор открыл вам меню выбора класса.");
                return true;
            }
            sender.sendMessage("§eИспользование: /classmenu [ник]");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("unlockclass")) {
            if (!sender.hasPermission("healkftwo.admin") && !sender.isOp()) {
                sender.sendMessage("§cУ вас нет прав на эту команду!");
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage("§eИспользование: /unlockclass <игрок> <класс>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage("§cИгрок не найден!"); return true; }

            String clazz = args[1].toLowerCase();
            List<String> unlockable = Arrays.asList("bomber", "ratcatcher", "werewolf", "snowman", "healer", "gorgon", "amphibian", "berserk", "mechanic");
            if (!unlockable.contains(clazz)) { sender.sendMessage("§cЭтот класс не существует или не является закрытым."); return true; }

            getConfig().set("unlocked." + target.getUniqueId() + "." + clazz, true);
            saveConfig();

            sender.sendMessage("§aКласс " + clazz + " успешно открыт для " + target.getName());
            target.sendMessage("§eВам открыт класс: " + clazz);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("setclass")) {
            if (!sender.hasPermission("healkftwo.admin") && !sender.isOp()) {
                sender.sendMessage("§cУ вас нет прав на эту команду!");
                return true;
            }
            if (args.length != 2) { sender.sendMessage("§eИспользование: /setclass <игрок> <класс>"); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage("§cИгрок не найден!"); return true; }

            String clazz = args[1].toLowerCase();
            List<String> validClasses = Arrays.asList("archer", "swordsman", "alchemist", "lancer", "bomber",
                    "ratcatcher", "werewolf", "snowman", "healer", "gorgon", "amphibian", "berserk", "mechanic");
            if (!validClasses.contains(clazz)) {
                sender.sendMessage("§cНеверный класс!");
                return true;
            }

            boolean needsUnlockCheck = Arrays.asList("bomber", "ratcatcher", "werewolf", "snowman", "healer", "gorgon", "amphibian", "berserk", "mechanic").contains(clazz);
            if (needsUnlockCheck) {
                boolean unlocked = getConfig().getBoolean("unlocked." + target.getUniqueId() + "." + clazz, false);
                if (!unlocked) { sender.sendMessage("§cИгрок ещё не разблокировал класс " + clazz + "!"); return true; }
            }

            double hp = switch (clazz) {
                case "archer" -> 24.0;
                case "swordsman" -> 20.0;
                case "alchemist" -> 16.0;
                case "lancer" -> 20.0;
                case "bomber" -> 20.0;
                case "ratcatcher" -> 20.0;
                case "werewolf" -> 20.0;
                case "snowman" -> 24.0;
                case "healer" -> 22.0;
                case "gorgon" -> 18.0;
                case "amphibian" -> 20.0;
                case "berserk" -> 26.0;
                case "mechanic" -> 24.0;
                default -> 20.0;
            };

            assignClassToPlayer(target, clazz, hp);

            target.sendMessage("§eАдминистратор установил вам класс: " + clazz);
            sender.sendMessage("§aКласс игрока " + target.getName() + " успешно изменён на " + clazz);
            return true;
        }

        return false;
    }
}



