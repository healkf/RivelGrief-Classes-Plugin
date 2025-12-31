package healkf.healkftwo;

public final class Werewolf {
    private Werewolf() {}

    // бонусы/баланс — редактируй по вкусу
    public static final double DAMAGE_MULT_NIGHT = 1.30; // +30% урона ночью
    public static final int SPEED_AMPLIFIER = 2;
    public static final int SPEED_DURATION_TICKS = 20 * 20; // 20s
    public static final long SPEED_UPDATE_PERIOD_TICKS = 100L; // ~5s

    public static final int BLEED_SECONDS = 5;
    public static final double BLEED_PERCENT_PER_SECOND = 0.25;

    public static boolean isNight(long worldTime) {
        return worldTime >= 13000 && worldTime < 23000;
    }
}

