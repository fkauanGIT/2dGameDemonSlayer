package app.evoMouse.factory.EntitysObejcts;

import app.evoMouse.lighting.Light;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

import static app.evoMouse.IsometricRenderer.TILE_HEIGHT;
import static app.evoMouse.IsometricRenderer.TILE_WIDTH;

/**
 * Base de todo objeto do mundo que pode receber dano (árvores, pedras, inimigos...).
 * <p>
 * Controla o ciclo de vida do objeto:
 * <ul>
 *   <li><b>ALIVE</b> — recebe dano e é renderizado normalmente</li>
 *   <li><b>DYING</b> — toca a animação de morte; não recebe mais dano nem colide</li>
 *   <li><b>DEAD</b> — animação terminou; pode ser removido do mundo</li>
 * </ul>
 * Por padrão a morte é um fade-out. Subclasses com frames próprios de morte
 * sobrescrevem {@link #renderDying(SpriteBatch)} e ajustam {@link #deathDuration}.
 */
public abstract class WorldObject {

    public enum State {
        ALIVE, DYING, DEAD
    }

    protected float x, y;

    /**
     * Altura em que o objeto está (nível do terreno * altura do bloco). Só afeta o desenho
     */
    protected float z;

    protected Rectangle collisionBounds;
    protected Rectangle hitboxBounds;
    protected int hp;

    protected State state = State.ALIVE;

    /**
     * Tempo decorrido desde o início da morte
     */
    protected float deathTimer = 0f;

    /**
     * Duração total da animação de morte (em segundos)
     */
    protected float deathDuration = 0.6f;

    private final Color tmpColor = new Color();

    // ============================================================
    // === Ciclo de vida ==========================================
    // ============================================================

    /**
     * Atualiza o estado do objeto a cada frame.
     *
     * @param delta tempo (em segundos) desde o último frame
     */
    public void update(float delta) {
        if (state == State.DYING) {
            deathTimer += delta;
            if (deathTimer >= deathDuration) {
                state = State.DEAD;
            }
        }
    }

    /**
     * Aplica dano ao objeto. Ao zerar a vida, inicia a animação de morte.
     *
     * @param damage quantidade de dano
     */
    public void takeDamage(int damage) {
        if (state != State.ALIVE) return;

        hp -= damage;
        if (hp <= 0) {
            hp = 0;
            state = State.DYING;
            deathTimer = 0f;
            onDeath();
        }
    }

    /**
     * Chamado uma vez quando o objeto começa a morrer (loot, som, partículas...).
     */
    protected void onDeath() {
    }

    /**
     * @return progresso da animação de morte, de 0 (início) a 1 (fim)
     */
    protected float getDeathProgress() {
        return Math.min(deathTimer / deathDuration, 1f);
    }

    // ============================================================
    // === Renderização ==========================================
    // ============================================================

    public final void render(SpriteBatch batch) {
        switch (state) {
            case ALIVE -> renderAlive(batch);
            case DYING -> renderDying(batch);
            case DEAD -> {
            }
        }
    }

    /**
     * Desenha o objeto em seu estado normal.
     */
    protected abstract void renderAlive(SpriteBatch batch);

    /**
     * Desenha o objeto morrendo. Padrão: fade-out do sprite normal.
     */
    protected void renderDying(SpriteBatch batch) {
        tmpColor.set(batch.getColor());
        batch.setColor(tmpColor.r, tmpColor.g, tmpColor.b, tmpColor.a * (1f - getDeathProgress()));
        renderAlive(batch);
        batch.setColor(tmpColor);
    }

    // ============================================================
    // === Getters ===============================================
    // ============================================================

    public boolean isAlive() {
        return state == State.ALIVE;
    }

    public boolean isDead() {
        return state != State.ALIVE;
    }

    /**
     * @return true quando a animação de morte terminou e o objeto pode ser removido
     */
    public boolean isRemovable() {
        return state == State.DEAD;
    }

    public State getState() {
        return state;
    }

    /**
     * @return luz emitida pelo objeto neste frame, ou null se não emite luz
     */
    public Light getLight() {
        return null;
    }

    /**
     * @return largura da sombra elíptica no chão (0 = sem sombra)
     */
    public float getShadowWidth() {
        return 0f;
    }

    /**
     * @return opacidade atual do objeto (1 vivo, caindo até 0 durante a morte)
     */
    public float getVisibility() {
        return switch (state) {
            case ALIVE -> 1f;
            case DYING -> 1f - getDeathProgress();
            case DEAD -> 0f;
        };
    }

    /**
     * Ponto onde o objeto toca o chão (centro da face superior do tile por padrão).
     * Usado para sombra e ordem de desenho.
     */
    public float getBaseX() {
        return x + TILE_WIDTH / 2f;
    }

    public float getBaseY() {
        return y + TILE_HEIGHT * 0.75f;
    }

    public int getHp() {
        return hp;
    }

    public Rectangle getBoundsCollision() {
        return collisionBounds;
    }

    public Rectangle getBoundsHitbox() {
        return hitboxBounds;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    public void dispose() {
    }
}
