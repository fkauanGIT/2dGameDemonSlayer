package app.evoMouse.objects;

import app.evoMouse.effects.PixelParticles;
import app.evoMouse.factory.EntitysObejcts.WorldObject;
import app.evoMouse.lighting.Light;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;

import static app.evoMouse.IsometricRenderer.TILE_HEIGHT;
import static app.evoMouse.IsometricRenderer.TILE_WIDTH;

/**
 * Fogueira do refúgio: base de pedras e lenha (sprite) + chamas e brasas
 * (partículas) + luz quente tremulando. Indestrutível.
 */
public class CampfireObject extends WorldObject {

    private static final float PIXEL_SCALE = 2f;

    /**
     * Centro da fogueira na imagem, a partir do canto inferior esquerdo
     */
    private static final float ANCHOR_X = 16f, ANCHOR_Y = 16f;

    private static final Color FLAME_START = new Color(1f, 0.95f, 0.55f, 1f);
    private static final Color FLAME_END = new Color(0.85f, 0.15f, 0.05f, 1f);
    private static final Color EMBER_START = new Color(1f, 0.6f, 0.15f, 1f);
    private static final Color EMBER_END = new Color(0.6f, 0.1f, 0.05f, 1f);
    private static final Color SMOKE_START = new Color(0.35f, 0.33f, 0.33f, 0.5f);
    private static final Color SMOKE_END = new Color(0.25f, 0.25f, 0.27f, 0.3f);

    private final Texture texture;
    private final Light light;
    private final PixelParticles particles = new PixelParticles();

    private final float baseX, baseY;

    private float flameTimer, emberTimer, smokeTimer;

    public CampfireObject(float x, float y, float z, Texture texture) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.texture = texture;
        this.hp = 1;
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        baseX = x + TILE_WIDTH / 2f;
        baseY = y + TILE_HEIGHT * 0.75f;

        // Colisão: o anel de pedras. Sem hitbox de ataque (tamanho zero nunca sobrepõe)
        this.collisionBounds = new Rectangle(baseX - 22f, baseY - 14f, 44f, 26f);
        this.hitboxBounds = new Rectangle(baseX, baseY, 0f, 0f);

        this.light = new Light(baseX, baseY + z + 20f, 230f, new Color(1f, 0.6f, 0.25f, 1f), 0.12f);
    }

    @Override
    public void update(float delta) {
        flameTimer -= delta;
        emberTimer -= delta;
        smokeTimer -= delta;

        // Chamas: muitos pixels subindo rápido, do amarelo ao vermelho
        // As das bordas convergem para o centro e vivem menos: formato de chama
        while (flameTimer <= 0) {
            flameTimer += 0.008f;
            float spread = MathUtils.random(-1f, 1f);
            particles.emit(baseX + spread * 14f - 2f, baseY + z + MathUtils.random(0f, 8f),
                    -spread * 16f, MathUtils.random(35f, 60f),
                    MathUtils.random(0.45f, 0.85f) * (1f - Math.abs(spread) * 0.6f),
                    MathUtils.randomBoolean(0.4f) ? 3f : 2f, FLAME_START, FLAME_END);
        }

        // Brasas: poucas, sobem alto e balançam
        if (emberTimer <= 0) {
            emberTimer = MathUtils.random(0.12f, 0.35f);
            PixelParticles.Particle p = particles.emit(baseX + MathUtils.random(-8f, 8f), baseY + z + 10f,
                    MathUtils.random(-10f, 10f), MathUtils.random(50f, 80f),
                    MathUtils.random(0.9f, 1.6f), 1f, EMBER_START, EMBER_END);
            PixelParticles.setSway(p, 14f, 5f);
        }

        // Fumaça: lenta, acima das chamas
        if (smokeTimer <= 0) {
            smokeTimer = MathUtils.random(0.25f, 0.5f);
            PixelParticles.Particle p = particles.emit(baseX + MathUtils.random(-6f, 6f), baseY + z + 40f,
                    MathUtils.random(2f, 8f), MathUtils.random(18f, 28f),
                    MathUtils.random(1.5f, 2.5f), 3f, SMOKE_START, SMOKE_END);
            PixelParticles.setSway(p, 8f, 1.5f);
        }

        particles.update(delta);
    }

    @Override
    public void takeDamage(int damage) {
        // Indestrutível
    }

    @Override
    protected void renderAlive(SpriteBatch batch) {
        float width = texture.getWidth() * PIXEL_SCALE;
        float height = texture.getHeight() * PIXEL_SCALE;
        batch.draw(texture, baseX - ANCHOR_X * PIXEL_SCALE, baseY + z - ANCHOR_Y * PIXEL_SCALE, width, height);
        particles.render(batch);
    }

    @Override
    public Light getLight() {
        return light;
    }

    @Override
    public float getBaseX() {
        return baseX;
    }

    @Override
    public float getBaseY() {
        return baseY;
    }
}
