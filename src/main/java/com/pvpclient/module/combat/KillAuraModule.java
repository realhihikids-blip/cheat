package com.pvpclient.module.combat;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.module.bot.BotGuard;
import com.pvpclient.setting.ModeSetting;
import com.pvpclient.setting.BooleanSetting;
import com.pvpclient.setting.NumberSetting;
import com.pvpclient.util.AimSmoother;
import com.pvpclient.util.CombatUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class KillAuraModule extends ClientModule {
    // Режим: Legit — плавное наведение (как раньше); Krutilka — жёсткий агрессивный режим
    private final ModeSetting mode = add(new ModeSetting("Mode", 0, "Legit", "Krutilka"));
    private final NumberSetting chaseRange = add(new NumberSetting("Chase Range", 12.0, 4.0, 24.0, 1.0));
    private final NumberSetting spinRadius = add(new NumberSetting("Spin Radius", 2.4, 1.2, 4.0, 0.1));
    private final NumberSetting spinReach = add(new NumberSetting("Spin Reach", 3.2, 2.5, 6.0, 0.1));
    private final BooleanSetting autoJump = add(new BooleanSetting("Auto Jump Crits", true));
    private final NumberSetting range = add(new NumberSetting("Attack Range", 3.1, 2.0, 4.5, 0.1));
    private final NumberSetting fov = add(new NumberSetting("Attack FOV", 120.0, 30.0, 360.0, 5.0));
    private final NumberSetting smoothSpeed = add(new NumberSetting("Aim Smooth", 15.0, 5.0, 30.0, 1.0));
    private final BooleanSetting onlyCrits = add(new BooleanSetting("Only Crits", true));

    private final AimSmoother smoother = new AimSmoother();
    private int hitTicks = 0;
    private double randomDelay = 0;

    public KillAuraModule() {
        super("KillAura", "Плавная киллаура с рандомизацией для обхода Matrix/Vulcan", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        smoother.reset();
        hitTicks = 0;
        releaseSpinKeys();
        locked = null;
    }

    // ---------------------------------------------------------------
    //  КРУТИЛКА: жёсткий режим. Мгновенный захват цели, погоня, кружение,
    //  постоянные прыжки и удар критом в падении. Только singleplayer / LAN / authorizedServers.
    // ---------------------------------------------------------------
    private LivingEntity locked;
    private boolean spinKeysHeld;
    private boolean circleLeft = true;
    private int circleTicks, warnTicks;

    private void releaseSpinKeys() {
        if (!spinKeysHeld) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        spinKeysHeld = false;
    }

    private void spin() {
        if (!BotGuard.allowed(mc)) {
            if (warnTicks++ % 60 == 0) mc.player.sendMessage(Text.literal("KillAura «Крутилка»: только singleplayer / LAN / authorizedServers"), true);
            releaseSpinKeys();
            return;
        }
        // 1) Захват цели: держим одну цель, пока она жива и в пределах погони
        if (locked == null || !locked.isAlive() || mc.player.distanceTo(locked) > chaseRange.get() + 4.0) {
            locked = CombatUtil.findTarget(mc, chaseRange.get(), 360.0, 0, false);
        }
        if (locked == null) { releaseSpinKeys(); return; }

        // 2) Жёсткое мгновенное наведение на центр хитбокса
        Vec3d eye = mc.player.getEyePos();
        Vec3d c = locked.getBoundingBox().getCenter();
        double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
        mc.player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        mc.player.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

        // 3) Погоня и кружение вокруг цели (взгляд всегда на ней → A/D даёт движение по кругу)
        double dist = CombatUtil.distanceToBox(eye, locked);
        double r = spinRadius.get();
        if (mc.player.horizontalCollision || ++circleTicks > 50) { circleLeft = !circleLeft; circleTicks = 0; }
        mc.options.forwardKey.setPressed(dist > r + 0.2);
        mc.options.backKey.setPressed(dist < r - 0.9);
        mc.options.leftKey.setPressed(dist < chaseRange.get() && circleLeft);
        mc.options.rightKey.setPressed(dist < chaseRange.get() && !circleLeft);
        // без спринта: в ванили удар на бегу не может быть критом
        mc.options.sprintKey.setPressed(false);
        mc.player.setSprinting(false);
        // 4) Сам прыгает
        mc.options.jumpKey.setPressed(autoJump.get() && mc.player.isOnGround());
        spinKeysHeld = true;

        // 5) Удар критом: в падении, при заряженном кулдауне
        boolean falling = !mc.player.isOnGround() && mc.player.getVelocity().y < 0 && mc.player.fallDistance > 0.0f;
        boolean ready = mc.player.getAttackCooldownProgress(0.5f) >= 0.92f;
        if (dist <= spinReach.get() && ready && (falling || !autoJump.get())) {
            CombatUtil.attack(mc, locked);
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (mode.getMode().equals("Krutilka")) { spin(); return; }
        releaseSpinKeys();

        hitTicks++;

        // 1. Ищем цель (передаем 5 параметров под твой сурс)
        LivingEntity victim = CombatUtil.findTarget(mc, range.get(), fov.get(), 0, true);
        if (victim == null) {
            smoother.reset();
            return;
        }

        // 2. Рассчитываем углы до хитбокса врага
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPoint = victim.getBoundingBox().getCenter();
        
        double dX = targetPoint.x - eyePos.x;
        double dY = targetPoint.y - eyePos.y;
        double dZ = targetPoint.z - eyePos.z;
        double horizontal = Math.sqrt(dX * dX + dZ * dZ);
        
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dZ, dX)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dY, horizontal)));

        // 3. Плавное легитное наведение камеры (без дёрганий и крашей пакетов)
        smoother.towards(targetYaw, targetPitch, smoothSpeed.get(), 0.05);
        
        mc.player.setYaw(smoother.getYaw());
        mc.player.setPitch(smoother.getPitch());

        // 4. Проверяем, наведён ли прицел близко к хитбоксу перед атакой
        float deltaYaw = Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - targetYaw));
        if (deltaYaw > 35.0f) return; 

        // 5. Рандомизация ударов (плавающий кулдаун для обмана Matrix)
        if (hitTicks >= (10 + randomDelay)) {
            if (onlyCrits.get() && mc.player.isOnGround()) {
                mc.player.jump();
                return;
            }

            // Наносим удар
            CombatUtil.attack(mc, victim);
            
            // Задаем случайное смещение для следующего удара
            randomDelay = Math.random() * 3.0; 
            hitTicks = 0;
        }
    }
}