package app.evoMouse.factory.CombatSystem;

import app.evoMouse.effects.WaterSlash;
import app.evoMouse.enemies.OniEnemy;
import app.evoMouse.factory.EntitysObejcts.WorldObject;
import app.evoMouse.player.Clone;
import app.evoMouse.player.Player;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

public class CombatSystem {
    public static void handleAttacks(Player player, Array<WorldObject> objects,
                                     Array<Rectangle> collisions, Array<Rectangle> hitboxes) {

        WaterSlash slash = player.getWaterSlash();
        if (slash.isHitting()) {
            handleWaterSlash(player, slash, objects, collisions, hitboxes);
        } else if (player.isAttacking() && !player.hasHitThisAttack() && !slash.isActive()) {
            if (hit(player.getBounds(), player.getAttackDamage(), objects, collisions, hitboxes)) {
                player.setHasHitThisAttack(true);
            }
        }

        // Cada clone tem hitbox própria: 3 corpos acertando o mesmo alvo = 3x o dano
        for (Clone clone : player.getClones()) {
            if (!clone.isAttacking() || clone.hasHitThisAttack()) continue;
            if (hit(clone.getBounds(), clone.getAttackDamage(), objects, collisions, hitboxes)) {
                clone.setHasHitThisAttack(true);
            }
        }
    }

    /**
     * Corte da Maré: acerta tudo dentro da área (uma vez por golpe) e arremessa os Oni
     * na direção oposta ao jogador.
     */
    private static void handleWaterSlash(Player player, WaterSlash slash, Array<WorldObject> objects,
                                         Array<Rectangle> collisions, Array<Rectangle> hitboxes) {
        for (WorldObject obj : objects) {
            if (!obj.isAlive() || !slash.getArea().overlaps(obj.getBoundsHitbox())) continue;
            if (!slash.markHit(obj)) continue;

            obj.takeDamage(WaterSlash.DAMAGE);

            if (obj instanceof OniEnemy oni) {
                float dx = oni.getBaseX() - player.getFeetX(), dy = oni.getBaseY() - player.getFeetY();
                float len = Math.max(1f, (float) Math.hypot(dx, dy));
                oni.knockback(dx / len, dy / len, WaterSlash.KNOCKBACK_SPEED, WaterSlash.KNOCKBACK_LIFT);
            }

            if (obj.isDead()) {
                collisions.removeValue(obj.getBoundsCollision(), true);
                hitboxes.removeValue(obj.getBoundsHitbox(), true);
            }
        }
    }

    /**
     * Aplica o dano ao primeiro objeto vivo que a hitbox atingir.
     * Inimigos têm prioridade: lutando perto de uma árvore, o golpe vai no Oni, não no tronco.
     *
     * @return true se acertou algum objeto
     */
    private static boolean hit(Rectangle attackHitbox, int damage, Array<WorldObject> objects,
                               Array<Rectangle> collisions, Array<Rectangle> hitboxes) {
        return hitFirst(attackHitbox, damage, objects, collisions, hitboxes, true)
                || hitFirst(attackHitbox, damage, objects, collisions, hitboxes, false);
    }

    private static boolean hitFirst(Rectangle attackHitbox, int damage, Array<WorldObject> objects,
                                    Array<Rectangle> collisions, Array<Rectangle> hitboxes, boolean enemies) {
        for (WorldObject obj : objects) {
            if (!obj.isAlive() || (obj instanceof OniEnemy) != enemies) continue;

            if (attackHitbox.overlaps(obj.getBoundsHitbox())) {
                obj.takeDamage(damage);

                // Ao morrer, deixa de colidir na hora; o objeto continua na lista
                // até a animação de morte terminar (ver removeFinished)
                if (obj.isDead()) {
                    collisions.removeValue(obj.getBoundsCollision(), true);
                    hitboxes.removeValue(obj.getBoundsHitbox(), true);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Remove do mundo os objetos cuja animação de morte já terminou.
     */
    public static void removeFinished(Array<WorldObject> objects) {
        for (int i = objects.size - 1; i >= 0; i--) {
            WorldObject obj = objects.get(i);
            if (obj.isRemovable()) {
                obj.dispose();
                objects.removeIndex(i);
            }
        }
    }
}
