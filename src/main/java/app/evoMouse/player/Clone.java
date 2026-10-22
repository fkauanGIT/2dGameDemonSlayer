package app.evoMouse.player;

import app.evoMouse.Terrain;
import app.evoMouse.lighting.Light;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * Clone do jogador invocado pelo "Selo do Reflexo".
 * <p>
 * O clone:
 * <ul>
 *   <li>Reaproveita as animações do {@link Player} (nenhuma sprite nova)</li>
 *   <li>Segue o dono mantendo um deslocamento fixo</li>
 *   <li>Repete os ataques do dono, com hitbox própria — 3 corpos = 3x o dano</li>
 *   <li>Surge com um clarão, dura {@link #LIFETIME} segundos e se desfaz</li>
 * </ul>
 */
public class Clone implements Entity {

    private enum State {
        SPAWNING, ACTIVE, VANISHING, DONE
    }

    /**
     * Tempo de vida do clone (em segundos), sem contar surgir/sumir
     */
    public static final float LIFETIME = 8f;

    private static final float SPAWN_TIME = 0.25f;
    private static final float VANISH_TIME = 0.3f;

    /**
     * Velocidade com que o clone alcança sua posição ao redor do dono
     */
    private static final float FOLLOW_SPEED = 12f;

    private final Player owner;
    private final Vector2 offset;
    private final Vector2 pos;
    private float z;
    private final Color tint = new Color();
    private final Light light = new Light(0, 0, 70f, Color.CYAN, 0f);

    private State state = State.SPAWNING;
    private float stateTimer = 0f;

    /**
     * Controle de acerto: cada clone acerta uma vez por golpe do dono
     */
    private int lastAttackId = -1;
    private boolean hasHitThisAttack = false;

    public Clone(Player owner, float offsetX, float offsetY) {
        this.owner = owner;
        this.offset = new Vector2(offsetX, offsetY);
        this.pos = new Vector2(owner.getIsoX() + offsetX, owner.getIsoY() + offsetY);
        this.z = owner.getZ();
    }

    // ============================================================
    // === Atualização ===========================================
    // ============================================================

    @Override
    public void update(float delta, Array<Rectangle> objectHitBoxes) {
        stateTimer += delta;

        switch (state) {
            case SPAWNING -> {
                if (stateTimer >= SPAWN_TIME) changeState(State.ACTIVE);
            }
            case ACTIVE -> {
                if (stateTimer >= LIFETIME) changeState(State.VANISHING);
            }
            case VANISHING -> {
                if (stateTimer >= VANISH_TIME) changeState(State.DONE);
            }
            case DONE -> {
                return;
            }
        }

        // Segue o dono suavemente (sem colisão: o clone atravessa o cenário)
        float targetX = owner.getIsoX() + offset.x;
        float targetY = owner.getIsoY() + offset.y;
        float t = Math.min(1f, FOLLOW_SPEED * delta);
        pos.x += (targetX - pos.x) * t;
        pos.y += (targetY - pos.y) * t;

        // Altura: nível do chão sob o clone; fora do mapa, acompanha a do dono
        Terrain terrain = owner.getTerrain();
        float surface = terrain == null ? -1f : terrain.surfaceAt(pos.x, pos.y);
        float targetZ = surface >= 0 ? surface * Terrain.LEVEL_HEIGHT : owner.getZ();
        z += (targetZ - z) * Math.min(1f, 14f * delta);

        // Novo golpe do dono = novo acerto liberado para o clone
        if (owner.getAttackId() != lastAttackId) {
            lastAttackId = owner.getAttackId();
            hasHitThisAttack = false;
        }
    }

    private void changeState(State next) {
        state = next;
        stateTimer = 0f;
    }

    /**
     * Desfaz o clone antes do tempo (ex: ao receber dano ou lançar o selo de novo).
     */
    public void dispel() {
        if (state == State.SPAWNING || state == State.ACTIVE) changeState(State.VANISHING);
    }

    // ============================================================
    // === Renderização ==========================================
    // ============================================================

    @Override
    public void render(SpriteBatch batch) {
        float alpha;
        switch (state) {
            case SPAWNING -> {
                // Clarão branco que vai assumindo o tom do clone
                float p = stateTimer / SPAWN_TIME;
                tint.set(1f, 1f, 1f, p).lerp(0.7f, 0.85f, 1f, 0.85f, p);
                owner.renderFrameAt(batch, pos.x, pos.y + z, tint);
                return;
            }
            case ACTIVE -> alpha = 0.85f;
            case VANISHING -> alpha = 0.85f * (1f - stateTimer / VANISH_TIME);
            default -> {
                return;
            }
        }
        tint.set(0.7f, 0.85f, 1f, alpha);
        owner.renderFrameAt(batch, pos.x, pos.y + z, tint);
    }

    // ============================================================
    // === Combate ===============================================
    // ============================================================

    /**
     * @return true se o clone está atacando junto com o dono neste frame
     */
    public boolean isAttacking() {
        return state == State.ACTIVE && owner.isAttacking();
    }

    public boolean hasHitThisAttack() {
        return hasHitThisAttack;
    }

    public void setHasHitThisAttack(boolean value) {
        this.hasHitThisAttack = value;
    }

    public int getAttackDamage() {
        return owner.getAttackDamage();
    }

    public Rectangle getBounds() {
        return Player.boundsAt(pos.x, pos.y);
    }

    // ============================================================
    // === Getters ===============================================
    // ============================================================

    /**
     * @return brilho azulado ao redor do clone, acompanhando o fade de surgir/sumir
     */
    public Light getLight() {
        float strength = getOpacity();
        if (strength <= 0f) return null;

        light.x = pos.x + 32f;
        light.y = pos.y + z + 60f;
        light.color.set(0.35f * strength, 0.6f * strength, 1f * strength, 1f);
        return light;
    }

    public boolean isDone() {
        return state == State.DONE;
    }

    /**
     * @return opacidade atual do clone (0 a 1), acompanhando surgir/sumir
     */
    public float getOpacity() {
        return switch (state) {
            case SPAWNING -> stateTimer / SPAWN_TIME;
            case ACTIVE -> 1f;
            case VANISHING -> 1f - stateTimer / VANISH_TIME;
            case DONE -> 0f;
        };
    }

    @Override
    public float getIsoX() {
        return pos.x;
    }

    @Override
    public float getIsoY() {
        return pos.y;
    }

    @Override
    public float getZ() {
        return z;
    }
}
