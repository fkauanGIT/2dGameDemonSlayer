package app.evoMouse.lighting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * Luz pontual suave (tocha, lanterna, brilho mágico...).
 */
public class Light {

    public float x, y;
    public float radius;
    public final Color color = new Color();

    /**
     * Intensidade da oscilação (0 = luz estável, 0.15 = chama tremendo)
     */
    public float flicker;

    private float time = MathUtils.random(100f);

    public Light(float x, float y, float radius, Color color, float flicker) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.color.set(color);
        this.flicker = flicker;
    }

    public void update(float delta) {
        time += delta;
    }

    /**
     * @return raio atual, já com a oscilação aplicada
     */
    public float getCurrentRadius() {
        if (flicker == 0) return radius;
        float wave = MathUtils.sin(time * 9f) * 0.5f + MathUtils.sin(time * 23f) * 0.3f;
        return radius * (1f + wave * flicker);
    }
}
