package app.evoMouse.effects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

/**
 * Sistema simples de partículas quadradas (pixel art): fogo, brasas, pétalas, folhas...
 * <p>
 * Cada partícula é um quadrado de cor sólida que se move, muda de cor e some.
 * As posições são arredondadas para múltiplos de {@link #PIXEL} para manter o
 * visual de pixel art (mesma escala 2x do mapa).
 */
public class PixelParticles {

    /**
     * Tamanho de um "pixel" de arte no mundo (sprites são desenhados em 2x)
     */
    public static final float PIXEL = 2f;

    private static Texture whitePixel;

    private final Array<Particle> particles = new Array<>();
    private final Pool<Particle> pool = new Pool<>() {
        @Override
        protected Particle newObject() {
            return new Particle();
        }
    };

    private final Color tmp = new Color();

    public static class Particle {
        float x, y, vx, vy;
        float gravity;
        float life, maxLife;
        float size;
        /**
         * Oscilação lateral (pétalas balançando ao cair)
         */
        float sway, swaySpeed, swayPhase;
        final Color startColor = new Color();
        final Color endColor = new Color();
    }

    /**
     * Emite uma partícula.
     *
     * @param size  tamanho em pixels de arte (1 = um pixel da sprite)
     * @param start cor ao nascer
     * @param end   cor ao morrer (o alpha some até 0 no final da vida)
     */
    public Particle emit(float x, float y, float vx, float vy, float life, float size, Color start, Color end) {
        Particle p = pool.obtain();
        p.x = x;
        p.y = y;
        p.vx = vx;
        p.vy = vy;
        p.gravity = 0f;
        p.life = 0f;
        p.maxLife = life;
        p.size = size * PIXEL;
        p.sway = 0f;
        p.swaySpeed = 0f;
        p.swayPhase = MathUtils.random(MathUtils.PI2);
        p.startColor.set(start);
        p.endColor.set(end);
        particles.add(p);
        return p;
    }

    /**
     * Faz a partícula balançar de um lado para o outro enquanto se move.
     */
    public static void setSway(Particle p, float amplitude, float speed) {
        p.sway = amplitude;
        p.swaySpeed = speed;
    }

    public static void setGravity(Particle p, float gravity) {
        p.gravity = gravity;
    }

    public void update(float delta) {
        for (int i = particles.size - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life += delta;
            if (p.life >= p.maxLife) {
                particles.removeIndex(i);
                pool.free(p);
                continue;
            }
            p.vy -= p.gravity * delta;
            p.x += (p.vx + MathUtils.sin(p.life * p.swaySpeed + p.swayPhase) * p.sway) * delta;
            p.y += p.vy * delta;
        }
    }

    public void render(SpriteBatch batch) {
        if (particles.isEmpty()) return;

        Color batchColor = batch.getColor();
        float r = batchColor.r, g = batchColor.g, b = batchColor.b, a = batchColor.a;

        for (Particle p : particles) {
            float t = p.life / p.maxLife;
            tmp.set(p.startColor).lerp(p.endColor, t);
            batch.setColor(tmp.r * r, tmp.g * g, tmp.b * b, tmp.a * (1f - t * t) * a);

            // Encaixa na grade de pixels da arte
            float px = MathUtils.floor(p.x / PIXEL) * PIXEL;
            float py = MathUtils.floor(p.y / PIXEL) * PIXEL;
            batch.draw(getWhitePixel(), px, py, p.size, p.size);
        }

        batch.setColor(r, g, b, a);
    }

    public boolean isEmpty() {
        return particles.isEmpty();
    }

    private static Texture getWhitePixel() {
        if (whitePixel == null) {
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.WHITE);
            pixmap.fill();
            whitePixel = new Texture(pixmap);
            pixmap.dispose();
        }
        return whitePixel;
    }
}
