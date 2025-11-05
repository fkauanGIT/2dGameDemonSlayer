package app.evoMouse.player;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

public interface Entity {
    void render(SpriteBatch batch);
    void update(float delta, Array<Rectangle> objectHitBoxes);
}
