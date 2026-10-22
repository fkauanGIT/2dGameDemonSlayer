package app.evoMouse.effects;

import app.evoMouse.lighting.Light;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ObjectSet;

/**
 * Técnica "Kata da Água: Corte da Maré".
 * <p>
 * A espada desenha um arco de água na frente do jogador (crista branca, corpo azul,
 * espuma) que termina num respingo. Tudo em partículas de pixel, sem sprites extras.
 * Durante a janela de acerto, tudo dentro de {@link #getArea()} leva dano e os Oni
 * são arremessados para longe.
 */
public class WaterSlash {

    public static final float KI_COST = 25f;
    public static final int DAMAGE = 35;

    /**
     * Velocidade com que o Oni é arremessado (no chão) e para cima
     */
    public static final float KNOCKBACK_SPEED = 380f;
    public static final float KNOCKBACK_LIFT = 230f;

    private static final float DURATION = 0.45f;
    private static final float SWEEP_TIME = 0.22f;
    private static final float HIT_START = 0.05f, HIT_END = 0.3f;

    /**
     * Abertura do arco (graus para cada lado da direção em que o jogador olha)
     */
    private static final float ARC_HALF = 110f;
    private static final float RADIUS = 44f;

    private static final Color CREST = new Color(0.92f, 0.98f, 1f, 1f);
    private static final Color CREST_END = new Color(0.55f, 0.85f, 1f, 1f);
    private static final Color BODY = new Color(0.2f, 0.55f, 1f, 1f);
    private static final Color BODY_END = new Color(0.08f, 0.25f, 0.75f, 1f);
    private static final Color DEEP = new Color(0.1f, 0.3f, 0.85f, 1f);
    private static final Color SPRAY = new Color(0.7f, 0.9f, 1f, 1f);

    private final PixelParticles particles = new PixelParticles();
    private final Light light = new Light(0, 0, 140f, new Color(0.3f, 0.6f, 1f, 1f), 0.05f);
    private final Rectangle area = new Rectangle();
    private final ObjectSet<Object> alreadyHit = new ObjectSet<>();

    private boolean active = false;
    private boolean sprayed = false;
    private float time = 0f;
    private float facingDeg;
    private float centerX, centerY;

    /**
     * Começa o golpe.
     *
     * @param feetX     pés do jogador (X)
     * @param feetY     pés do jogador (Y, no plano do chão)
     * @param z         altura do jogador
     * @param facingDeg direção em que o jogador olha: 0 direita, 90 cima, 180 esquerda, 270 baixo
     */
    public void start(float feetX, float feetY, float z, float facingDeg) {
        this.facingDeg = facingDeg;
        active = true;
        sprayed = false;
        time = 0f;
        alreadyHit.clear();

        float rad = facingDeg * MathUtils.degreesToRadians;
        centerX = feetX + MathUtils.cos(rad) * 12f;
        centerY = feetY + z + 24f + MathUtils.sin(rad) * 6f;

        // Área de acerto no plano do chão, à frente do jogador
        float reach = 72f, width = 80f;
        switch (Math.round(facingDeg)) {
            case 0 -> area.set(feetX, feetY - width * 0.35f, reach, width * 0.7f);
            case 180 -> area.set(feetX - reach, feetY - width * 0.35f, reach, width * 0.7f);
            case 90 -> area.set(feetX - width / 2f, feetY - 8f, width, reach * 0.9f);
            default -> area.set(feetX - width / 2f, feetY - reach * 0.9f + 8f, width, reach * 0.9f);
        }

        light.x = centerX;
        light.y = centerY;
    }

    public void update(float delta) {
        particles.update(delta);
        if (!active) return;

        float before = sweepAngle(time);
        time += delta;
        float after = sweepAngle(time);

        // Enquanto a lâmina varre, preenche o trecho do arco percorrido neste frame
        if (time <= SWEEP_TIME + delta) emitArc(before, after);

        if (!sprayed && time >= SWEEP_TIME) {
            sprayed = true;
            emitSpray(after);
        }
        if (time >= DURATION) active = false;
    }

    /**
     * Ângulo da ponta da lâmina: varre de um lado ao outro, rápido no meio (smoothstep)
     */
    private float sweepAngle(float t) {
        float p = Math.min(1f, t / SWEEP_TIME);
        p = p * p * (3f - 2f * p);
        return facingDeg + ARC_HALF - p * ARC_HALF * 2f;
    }

    private void emitArc(float fromDeg, float toDeg) {
        int steps = 12;
        for (int i = 0; i <= steps; i++) {
            float deg = MathUtils.lerp(fromDeg, toDeg, i / (float) steps);
            float rad = deg * MathUtils.degreesToRadians;
            float cos = MathUtils.cos(rad), sin = MathUtils.sin(rad);

            // Tangente no sentido da varredura (horário) e direção para fora
            float tx = sin, ty = -cos;

            // Crista: borda externa clara
            emitOnArc(cos, sin, RADIUS + MathUtils.random(-2f, 3f), tx, ty, CREST, CREST_END, 1f, 0.3f);
            // Corpo da onda
            emitOnArc(cos, sin, RADIUS - MathUtils.random(4f, 14f), tx, ty, BODY, BODY_END,
                    MathUtils.randomBoolean(0.4f) ? 2f : 1f, 0.38f);
            // Redemoinho escuro por dentro
            if (MathUtils.randomBoolean(0.35f)) {
                PixelParticles.Particle p = emitOnArc(cos, sin, RADIUS - MathUtils.random(14f, 20f), tx, ty,
                        DEEP, BODY_END, 2f, 0.45f);
                PixelParticles.setSway(p, 20f, 9f);
            }
        }
    }

    private PixelParticles.Particle emitOnArc(float cos, float sin, float radius, float tx, float ty,
                                              Color start, Color end, float size, float life) {
        float px = centerX + cos * radius;
        float py = centerY + sin * radius * 0.72f; // arco achatado (perspectiva)
        return particles.emit(px, py,
                tx * 50f + cos * 20f, ty * 50f * 0.72f + sin * 14f,
                life * MathUtils.random(0.75f, 1.15f), size, start, end);
    }

    /**
     * Respingo na ponta final do arco: gotas que caem com gravidade
     */
    private void emitSpray(float tipDeg) {
        float rad = tipDeg * MathUtils.degreesToRadians;
        float tipX = centerX + MathUtils.cos(rad) * RADIUS;
        float tipY = centerY + MathUtils.sin(rad) * RADIUS * 0.72f;
        float forward = facingDeg * MathUtils.degreesToRadians;

        for (int i = 0; i < 22; i++) {
            float a = forward + MathUtils.random(-0.9f, 0.9f);
            float speed = MathUtils.random(60f, 150f);
            PixelParticles.Particle p = particles.emit(tipX + MathUtils.random(-6f, 6f), tipY + MathUtils.random(-4f, 4f),
                    MathUtils.cos(a) * speed, MathUtils.sin(a) * speed * 0.6f + MathUtils.random(40f, 110f),
                    MathUtils.random(0.4f, 0.7f), MathUtils.randomBoolean(0.3f) ? 2f : 1f, SPRAY, BODY);
            PixelParticles.setGravity(p, 320f);
        }
    }

    public void render(SpriteBatch batch) {
        particles.render(batch);
    }

    /**
     * @return true durante a janela em que o golpe causa dano
     */
    public boolean isHitting() {
        return active && time >= HIT_START && time <= HIT_END;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Área de acerto no plano do chão
     */
    public Rectangle getArea() {
        return area;
    }

    /**
     * Registra um alvo atingido; cada alvo só é atingido uma vez por golpe.
     *
     * @return true se ainda não tinha sido atingido
     */
    public boolean markHit(Object target) {
        return alreadyHit.add(target);
    }

    /**
     * @return clarão azul enquanto o golpe acontece (aparece à noite)
     */
    public Light getLight() {
        if (!active) return null;
        float fade = 1f - time / DURATION;
        light.color.set(0.3f * fade, 0.6f * fade, 1f * fade, 1f);
        return light;
    }
}
