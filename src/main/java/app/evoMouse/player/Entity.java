package app.evoMouse.player;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

public interface Entity {
    void render(SpriteBatch batch);
    void update(float delta, Array<Rectangle> objectHitBoxes);

    float getIsoX();

    /**
     * Posição Y no mundo isométrico, usada no Z-sorting.
     */
    float getIsoY();

    /**
     * Altura em que a entidade é desenhada (nível do terreno sob os pés).
     */
    float getZ();

    /**
     * Altura do chão sob a entidade, onde fica a sombra (difere de {@link #getZ()} no ar).
     */
    default float getShadowZ() {
        return getZ();
    }
}
