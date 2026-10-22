package app.evoMouse.objects;

import app.evoMouse.effects.PixelParticles;
import app.evoMouse.factory.EntitysObejcts.WorldObject;
import app.evoMouse.lighting.Light;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static app.evoMouse.IsometricRenderer.TILE_HEIGHT;
import static app.evoMouse.IsometricRenderer.TILE_WIDTH;

/**
 * Árvore do mapa. A aparência e o comportamento vêm do {@link TreeType}.
 * <p>
 * O centro do tronco (onde ele toca o chão) é ancorado no centro da face superior
 * do tile, então a árvore "pisa" no bloco independente do tamanho da imagem.
 * <p>
 * Animações feitas em código, sem frames extras:
 * <ul>
 *   <li><b>Vento</b> — o topo da sprite balança enquanto a base fica fixa</li>
 *   <li><b>Golpe</b> — a árvore treme e solta folhas</li>
 *   <li><b>Pétalas</b> — a sakura solta pétalas que caem balançando</li>
 * </ul>
 */
public class TreeObject extends WorldObject {

    /**
     * Escala dos pixels: mesma do chão (tile de 32px desenhado em 64px)
     */
    private static final float PIXEL_SCALE = 2f;

    private static final float SHAKE_TIME = 0.3f;

    private final TreeType type;
    private final Texture texture;
    private final Light light;
    private final PixelParticles particles = new PixelParticles();

    /**
     * Centro do tronco no chão (mundo)
     */
    private final float baseX, baseY;

    /**
     * Vértices da sprite (4 vértices x 5 valores: x, y, cor, u, v)
     */
    private final float[] vertices = new float[20];

    private float time;
    private final float windPhase;

    /**
     * Personagens que não podem sumir atrás de uma copa: {x na tela, y do corpo na tela, y dos pés no chão}.
     * Atualizado pelo renderer a cada frame.
     */
    private static final Array<float[]> focus = new Array<>();

    /**
     * Opacidade atual da árvore: fica translúcida quando alguém está atrás dela
     */
    private float canopyAlpha = 1f;
    private static final float CANOPY_SEE_THROUGH = 0.38f;
    private float shakeTimer = 0f;
    private float petalTimer = MathUtils.random(1f);

    public TreeObject(float x, float y, float z, TreeType type, Texture texture) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.texture = texture;
        this.hp = type.hp;
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        // Centro da face superior do tile, onde o tronco "pisa"
        baseX = x + TILE_WIDTH / 2f;
        baseY = y + TILE_HEIGHT * 0.75f;

        // Colisão: a área das raízes. Hitbox (alvo de ataques): um pouco maior
        float footW = type.footprintW * PIXEL_SCALE;
        float footH = type.footprintH * PIXEL_SCALE;
        this.collisionBounds = new Rectangle(baseX - footW / 2f, baseY - footH / 2f, footW, footH);
        this.hitboxBounds = new Rectangle(baseX - footW / 2f - 8f, baseY - footH / 2f - 6f, footW + 16f, footH + 12f);

        this.light = type.glowColor == null ? null
                : new Light(baseX, baseY + z + type.glowHeight, type.glowRadius, type.glowColor, 0.08f);

        // Árvores vizinhas não balançam em sincronia
        windPhase = (x * 0.013f + y * 0.029f) % MathUtils.PI2;
    }

    // ============================================================
    // === Atualização ===========================================
    // ============================================================

    @Override
    public void update(float delta) {
        super.update(delta);
        time += delta;
        if (shakeTimer > 0) shakeTimer -= delta;

        float target = hidesSomeone() ? CANOPY_SEE_THROUGH : 1f;
        canopyAlpha += (target - canopyAlpha) * Math.min(1f, 8f * delta);

        if (type.petals && isAlive()) {
            petalTimer -= delta;
            if (petalTimer <= 0) {
                petalTimer = MathUtils.random(0.4f, 1.2f);
                emitPetal();
            }
        }

        particles.update(delta);
    }

    @Override
    public void takeDamage(int damage) {
        if (!isAlive()) return;
        super.takeDamage(damage);
        shakeTimer = SHAKE_TIME;
        burstLeaves(isAlive() ? 8 : 24);
    }

    // ============================================================
    // === Renderização ==========================================
    // ============================================================

    @Override
    protected void renderAlive(SpriteBatch batch) {
        float width = texture.getWidth() * PIXEL_SCALE;
        float height = texture.getHeight() * PIXEL_SCALE;

        float left = baseX - type.anchorX * PIXEL_SCALE;
        float bottom = baseY + z - type.anchorY * PIXEL_SCALE;
        float right = left + width;
        float top = bottom + height;

        // Deslocamento do topo: vento (lento) + tremida do golpe (rápida, decaindo)
        float wind = (MathUtils.sin(time * 1.6f + windPhase) * 0.7f
                + MathUtils.sin(time * 0.6f + windPhase * 2f) * 0.3f) * type.sway;
        float shake = shakeTimer > 0
                ? MathUtils.sin(shakeTimer * 60f) * 5f * (shakeTimer / SHAKE_TIME) : 0f;
        float topOffset = wind + shake;

        Color base = batch.getColor();
        float r = base.r, g = base.g, b = base.b, a = base.a;
        batch.setColor(r, g, b, a * canopyAlpha);

        float color = batch.getPackedColor();
        setVertex(0, left, bottom, color, 0f, 1f);                // inferior esquerdo
        setVertex(1, left + topOffset, top, color, 0f, 0f);       // superior esquerdo
        setVertex(2, right + topOffset, top, color, 1f, 0f);      // superior direito
        setVertex(3, right, bottom, color, 1f, 1f);               // inferior direito

        batch.draw(texture, vertices, 0, vertices.length);
        batch.setColor(r, g, b, a);
        particles.render(batch);
    }

    /**
     * @return true se algum personagem está atrás da árvore, na altura da copa
     */
    private boolean hidesSomeone() {
        float canopyBottom = baseY + z + 24f;
        float canopyTop = baseY + z + (texture.getHeight() - type.anchorY) * PIXEL_SCALE;
        float halfWidth = type.canopyHalfWidth + 12f;
        for (float[] f : focus) {
            boolean behind = f[2] > baseY - 4f;
            boolean inside = Math.abs(f[0] - baseX) < halfWidth && f[1] > canopyBottom && f[1] < canopyTop;
            if (behind && inside) return true;
        }
        return false;
    }

    /**
     * Limpa a lista de personagens que as copas não devem esconder (chamar a cada frame).
     */
    public static void clearFocus() {
        focus.clear();
    }

    /**
     * Registra um personagem que as copas não devem esconder.
     *
     * @param screenX X do personagem
     * @param bodyY   Y do meio do corpo na tela (já com a altura)
     * @param groundY Y dos pés no plano do chão (para saber se está atrás do tronco)
     */
    public static void addFocus(float screenX, float bodyY, float groundY) {
        focus.add(new float[]{screenX, bodyY, groundY});
    }

    private void setVertex(int index, float vx, float vy, float color, float u, float v) {
        int i = index * 5;
        vertices[i] = vx;
        vertices[i + 1] = vy;
        vertices[i + 2] = color;
        vertices[i + 3] = u;
        vertices[i + 4] = v;
    }

    // ============================================================
    // === Partículas ============================================
    // ============================================================

    /**
     * Pétala nasce em algum ponto da copa e cai balançando até perto do chão.
     */
    private void emitPetal() {
        float startX = baseX + MathUtils.random(-type.canopyHalfWidth, type.canopyHalfWidth);
        float startY = baseY + z + type.canopyY + MathUtils.random(-30f, 30f);
        float fallSpeed = MathUtils.random(14f, 22f);
        float groundY = baseY + z + MathUtils.random(-16f, 8f);

        PixelParticles.Particle p = particles.emit(startX, startY, MathUtils.random(4f, 12f), -fallSpeed,
                (startY - groundY) / fallSpeed, 1f, type.leafColor, type.leafColor);
        PixelParticles.setSway(p, 18f, 3f);
    }

    /**
     * Folhas voando da copa (ao levar um golpe ou ser derrubada).
     */
    private void burstLeaves(int count) {
        for (int i = 0; i < count; i++) {
            float startX = baseX + MathUtils.random(-type.canopyHalfWidth, type.canopyHalfWidth) * 0.7f;
            float startY = baseY + z + type.canopyY + MathUtils.random(-20f, 20f);
            PixelParticles.Particle p = particles.emit(startX, startY,
                    MathUtils.random(-40f, 40f), MathUtils.random(10f, 50f),
                    MathUtils.random(0.6f, 1.1f), MathUtils.randomBoolean(0.3f) ? 2f : 1f,
                    type.leafColor, type.leafColor);
            PixelParticles.setGravity(p, 90f);
            PixelParticles.setSway(p, 12f, 4f);
        }
    }

    // ============================================================
    // === Getters ===============================================
    // ============================================================

    /**
     * @return luz emitida pela árvore (só enquanto viva), ou null
     */
    @Override
    public Light getLight() {
        return isAlive() ? light : null;
    }

    @Override
    public float getShadowWidth() {
        return type.shadowWidth * PIXEL_SCALE;
    }

    @Override
    public float getBaseX() {
        return baseX;
    }

    @Override
    public float getBaseY() {
        return baseY;
    }

    public TreeType getType() {
        return type;
    }

    /**
     * Sacode a árvore e solta folhas (ex: um Oni pulando da copa).
     */
    public void rustle() {
        shakeTimer = SHAKE_TIME;
        burstLeaves(16);
    }

    /**
     * @return altura (Y na tela) do centro da copa, já com a altura do terreno
     */
    public float getCanopyScreenY() {
        return baseY + z + type.canopyY;
    }

    /**
     * Tipos de árvore. Medidas em pixels da imagem original:
     * <ul>
     *   <li><b>anchor</b> — centro do tronco no chão, a partir do canto inferior esquerdo</li>
     *   <li><b>footprint</b> — largura/profundidade das raízes (vira a colisão)</li>
     *   <li><b>shadowWidth</b> — largura da sombra elíptica</li>
     * </ul>
     */
    public enum TreeType {
        //      textura                                    anchor   footprint  sombra  hp
        PINE("assets/landscape_elements/pine.png", 32, 13, 20, 7, 40, 100,
                3f, 100f, 40f, new Color(0.18f, 0.42f, 0.22f, 1f), false,
                null, 0, 0),
        SAKURA("assets/landscape_elements/sakura.png", 33, 19, 28, 7, 50, 80,
                4f, 90f, 50f, new Color(1f, 0.72f, 0.82f, 1f), true,
                null, 0, 0),
        CURSED("assets/landscape_elements/cursed_tree.png", 34, 14, 30, 9, 44, 150,
                1f, 90f, 45f, new Color(0.45f, 0.15f, 0.55f, 1f), false,
                new Color(0.6f, 0.2f, 1f, 1f), 90f, 60f);

        public final String texturePath;
        public final float anchorX, anchorY;
        public final float footprintW, footprintH;
        public final float shadowWidth;
        public final int hp;

        /**
         * Amplitude do balanço do vento no topo (em pixels do mundo)
         */
        public final float sway;

        /**
         * Altura do centro da copa acima da base e meia-largura da copa (mundo)
         */
        public final float canopyY, canopyHalfWidth;

        public final Color leafColor;
        public final boolean petals;

        public final Color glowColor;
        public final float glowRadius, glowHeight;

        TreeType(String texturePath, float anchorX, float anchorY,
                 float footprintW, float footprintH, float shadowWidth, int hp,
                 float sway, float canopyY, float canopyHalfWidth, Color leafColor, boolean petals,
                 Color glowColor, float glowRadius, float glowHeight) {
            this.texturePath = texturePath;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.footprintW = footprintW;
            this.footprintH = footprintH;
            this.shadowWidth = shadowWidth;
            this.hp = hp;
            this.sway = sway;
            this.canopyY = canopyY;
            this.canopyHalfWidth = canopyHalfWidth;
            this.leafColor = leafColor;
            this.petals = petals;
            this.glowColor = glowColor;
            this.glowRadius = glowRadius;
            this.glowHeight = glowHeight;
        }
    }
}
