package app.evoMouse.factory.EntitysObejcts;

import app.evoMouse.objects.TreeObject;
import app.evoMouse.objects.TreeObject.TreeType;
import com.badlogic.gdx.graphics.Texture;

public class WorldObjectFactory {
    /**
     * @param z altura do terreno onde o objeto fica (nível * altura do bloco)
     */
    public static WorldObject create(int type, float x, float y, float z, Texture texture) {
        return switch (type) {
            case 1 -> new TreeObject(x, y, z, TreeType.PINE, texture);
            case 2 -> new TreeObject(x, y, z, TreeType.SAKURA, texture);
            case 3 -> new TreeObject(x, y, z, TreeType.CURSED, texture);
//            case 4 -> new RockObject(x, y, z, texture);
            default -> null;
        };
    }
}
