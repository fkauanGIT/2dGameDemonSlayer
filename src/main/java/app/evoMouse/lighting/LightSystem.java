package app.evoMouse.lighting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;

/**
 * Iluminação 2D por light map, com ciclo de dia e noite.
 * <p>
 * Funcionamento:
 * <ol>
 *   <li>Um {@link FrameBuffer} é limpo com a cor ambiente (branco de dia, azul escuro à noite)</li>
 *   <li>Cada {@link Light} é somada nele (blend aditivo) como um gradiente radial</li>
 *   <li>O light map é multiplicado sobre a cena já desenhada</li>
 * </ol>
 * Onde há luz, a cena aparece com a cor original; onde não há, fica escura.
 */
public class LightSystem {

    /**
     * Duração de um dia completo (em segundos)
     */
    private static final float DAY_LENGTH = 240f;

    /**
     * Cores ambiente ao longo do dia: [momento (0-1), r, g, b].
     * 0 = meio-dia, 0.25 = pôr do sol, 0.5 = meia-noite, 0.75 = amanhecer
     */
    private static final float[][] AMBIENT_KEYS = {
            {0.00f, 1.00f, 1.00f, 1.00f},
            {0.18f, 1.00f, 0.95f, 0.90f},
            {0.25f, 0.85f, 0.55f, 0.45f},
            {0.32f, 0.22f, 0.22f, 0.40f},
            {0.50f, 0.10f, 0.12f, 0.28f},
            {0.68f, 0.22f, 0.22f, 0.40f},
            {0.75f, 0.80f, 0.60f, 0.60f},
            {0.82f, 1.00f, 0.95f, 0.90f},
            {1.00f, 1.00f, 1.00f, 1.00f},
    };

    private final FrameBuffer lightMap;
    private final Texture lightTexture;
    private final Matrix4 screenMatrix;
    private final int width, height;

    private final Color ambient = new Color(Color.WHITE);
    private final Array<Light> frameLights = new Array<>();

    /**
     * Momento atual do dia (0-1). Começa no fim da tarde
     */
    private float timeOfDay = 0.2f;

    /**
     * Multiplicador da velocidade do tempo (acelerar para testes)
     */
    private float timeScale = 1f;

    public LightSystem(int width, int height) {
        this.width = width;
        this.height = height;
        lightMap = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        lightTexture = createLightTexture(128);
        screenMatrix = new Matrix4().setToOrtho2D(0, 0, width, height);
    }

    // ============================================================
    // === Tempo ==================================================
    // ============================================================

    public void update(float delta) {
        timeOfDay = (timeOfDay + delta * timeScale / DAY_LENGTH) % 1f;
        updateAmbient();
    }

    private void updateAmbient() {
        for (int i = 0; i < AMBIENT_KEYS.length - 1; i++) {
            float[] a = AMBIENT_KEYS[i];
            float[] b = AMBIENT_KEYS[i + 1];
            if (timeOfDay >= a[0] && timeOfDay <= b[0]) {
                float t = (timeOfDay - a[0]) / (b[0] - a[0]);
                ambient.set(a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, a[3] + (b[3] - a[3]) * t, 1f);
                return;
            }
        }
    }

    /**
     * @return quão escuro está: 0 = dia pleno, 1 = meia-noite
     */
    public float getDarkness() {
        float brightness = (ambient.r + ambient.g + ambient.b) / 3f;
        return 1f - (brightness - 0.15f) / 0.85f;
    }

    public boolean isNight() {
        return getDarkness() > 0.5f;
    }

    public float getTimeOfDay() {
        return timeOfDay;
    }

    public void setTimeScale(float timeScale) {
        this.timeScale = timeScale;
    }

    // ============================================================
    // === Luzes ==================================================
    // ============================================================

    /**
     * Adiciona uma luz para o frame atual. Deve ser chamado a cada frame
     * (luzes de objetos que se movem ou somem são simplesmente não adicionadas).
     */
    public void addLight(Light light) {
        frameLights.add(light);
    }

    // ============================================================
    // === Renderização ==========================================
    // ============================================================

    /**
     * Aplica a iluminação sobre a cena já desenhada na tela.
     * Chamar depois de desenhar o mundo e antes do HUD.
     */
    public void render(SpriteBatch batch, OrthographicCamera camera, float delta) {
        // 1. Monta o light map: ambiente + luzes somadas
        lightMap.begin();
        Gdx.gl.glClearColor(ambient.r, ambient.g, ambient.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        for (Light light : frameLights) {
            light.update(delta);
            float r = light.getCurrentRadius();
            batch.setColor(light.color);
            batch.draw(lightTexture, light.x - r, light.y - r, r * 2, r * 2);
        }
        batch.end();
        lightMap.end();

        // 2. Multiplica o light map sobre a cena
        batch.setProjectionMatrix(screenMatrix);
        batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO);
        batch.setColor(Color.WHITE);
        batch.begin();
        batch.draw(lightMap.getColorBufferTexture(), 0, 0, width, height, 0, 0, 1, 1); // FBO vem invertido em Y
        batch.end();

        // Restaura o estado padrão do batch
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        frameLights.clear();
    }

    /**
     * Gera a textura da luz: um círculo branco que esmaece suavemente até a borda.
     */
    private static Texture createLightTexture(int size) {
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float center = size / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dist = (float) Math.hypot(x + 0.5f - center, y + 0.5f - center) / center;
                float falloff = Math.max(0f, 1f - dist);
                float alpha = falloff * falloff * (3f - 2f * falloff); // smoothstep
                pixmap.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, alpha));
            }
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    public void dispose() {
        lightMap.dispose();
        lightTexture.dispose();
    }
}
