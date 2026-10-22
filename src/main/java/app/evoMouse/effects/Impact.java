package app.evoMouse.effects;

import com.badlogic.gdx.math.MathUtils;

/**
 * "Peso" dos golpes: tremor de câmera e hit-stop (congelar o mundo por um instante).
 * <p>
 * Qualquer sistema pode disparar ({@link #shake}, {@link #freeze}); o {@code GameScreen}
 * consome a cada frame. Estático de propósito: é um efeito global da tela.
 */
public final class Impact {

    /**
     * Deslocamento máximo da câmera com trauma 1 (em pixels do mundo)
     */
    private static final float MAX_SHAKE = 10f;

    /**
     * Quanto do trauma some por segundo
     */
    private static final float SHAKE_DECAY = 2.2f;

    private static float trauma = 0f;
    private static float freezeTimer = 0f;
    private static float time = 0f;

    private Impact() {
    }

    /**
     * Adiciona tremor (0 a 1). Tremores somam, até o máximo de 1.
     */
    public static void shake(float amount) {
        trauma = Math.min(1f, trauma + amount);
    }

    /**
     * Congela o mundo por alguns segundos (hit-stop). Mantém o maior pedido.
     */
    public static void freeze(float seconds) {
        freezeTimer = Math.max(freezeTimer, seconds);
    }

    /**
     * @return delta que o mundo deve usar neste frame (0 durante o hit-stop)
     */
    public static float worldDelta(float delta) {
        if (freezeTimer > 0f) {
            freezeTimer -= delta;
            return 0f;
        }
        return delta;
    }

    public static boolean isFrozen() {
        return freezeTimer > 0f;
    }

    public static void update(float delta) {
        time += delta;
        trauma = Math.max(0f, trauma - SHAKE_DECAY * delta);
    }

    /**
     * Deslocamento da câmera neste frame. Trauma ao quadrado: tremores fracos quase
     * não aparecem, fortes chacoalham bem.
     */
    public static float offsetX() {
        return MAX_SHAKE * trauma * trauma * (MathUtils.sin(time * 61f) * 0.6f + MathUtils.sin(time * 37f) * 0.4f);
    }

    public static float offsetY() {
        return MAX_SHAKE * trauma * trauma * (MathUtils.sin(time * 53f + 1.3f) * 0.6f + MathUtils.sin(time * 29f) * 0.4f);
    }
}
