package app.evoMouse.enemies;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;

import java.util.EnumMap;
import java.util.Map;

/**
 * Sprites e animações do Oni, carregados de {@code assets/enemies/oni/}:
 * <pre>
 * idle/{south,east,north,west}.png        pose parada (uma por direção)
 * walk/{direção}/{0..n}.png               andar
 * attack/{direção}/{0..n}.png             golpe
 * jump/south/{0..n}.png                   pulo da árvore (só de frente)
 * death/south/{0..n}.png                  morte (só de frente)
 * </pre>
 * Animações ou direções que faltarem usam a pose parada, então o jogo
 * funciona mesmo com parte dos sprites ainda por fazer.
 * Uma única instância é compartilhada por todos os Oni.
 */
public class OniSprites {

    private static final String ROOT = "assets/enemies/oni/";

    /**
     * Primeiro frame do pulo em que o Oni já está no ar
     */
    private static final int JUMP_AIR_FRAME = 3;

    public enum Dir {
        SOUTH("south"), EAST("east"), NORTH("north"), WEST("west");

        final String folder;

        Dir(String folder) {
            this.folder = folder;
        }

        /**
         * Direção dominante de um vetor no plano da tela (y para cima = norte)
         */
        public static Dir fromVector(float dx, float dy) {
            if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? EAST : WEST;
            return dy > 0 ? NORTH : SOUTH;
        }
    }

    private final Array<Texture> textures = new Array<>();
    private final Map<Dir, TextureRegion> idle = new EnumMap<>(Dir.class);
    private final Map<Dir, Animation<TextureRegion>> walk = new EnumMap<>(Dir.class);
    private final Map<Dir, Animation<TextureRegion>> attack = new EnumMap<>(Dir.class);
    private final Animation<TextureRegion> jump;
    private final Animation<TextureRegion> death;
    private final Texture pixel;

    public OniSprites() {
        for (Dir dir : Dir.values()) {
            idle.put(dir, loadRegion(ROOT + "idle/" + dir.folder + ".png"));
        }
        for (Dir dir : Dir.values()) {
            walk.put(dir, loadAnimation("walk/" + dir.folder, 0.12f, idle.get(dir)));
            attack.put(dir, loadAnimation("attack/" + dir.folder, 0.08f, idle.get(dir)));
        }
        jump = loadAnimation("jump/south", 0.08f, idle.get(Dir.SOUTH));
        death = loadAnimation("death/south", 0.12f, idle.get(Dir.SOUTH));

        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        p.fill();
        pixel = new Texture(p);
        p.dispose();
    }

    private TextureRegion loadRegion(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        textures.add(texture);
        return new TextureRegion(texture);
    }

    /**
     * Carrega 0.png, 1.png, ... até não existir o próximo. Sem nenhum frame, usa o fallback.
     */
    private Animation<TextureRegion> loadAnimation(String folder, float frameTime, TextureRegion fallback) {
        Array<TextureRegion> frames = new Array<>();
        for (int i = 0; ; i++) {
            FileHandle file = Gdx.files.internal(ROOT + folder + "/" + i + ".png");
            if (!file.exists()) break;
            frames.add(loadRegion(file.path()));
        }
        if (frames.isEmpty()) frames.add(fallback);
        return new Animation<>(frameTime, frames);
    }

    public TextureRegion walk(Dir dir, float time) {
        return walk.get(dir).getKeyFrame(time, true);
    }

    public TextureRegion attack(Dir dir, float time) {
        return attack.get(dir).getKeyFrame(time, false);
    }

    /**
     * Frame do pulo. A animação tem 9 frames: 0-2 impulso (agacha), 3-6 no ar, 7-8 aterrissagem.
     *
     * @param progress progresso do pulo, de 0 a 1
     * @param fromAir  true quando o pulo já começa no ar (caindo da árvore): pula o impulso
     */
    public TextureRegion jump(float progress, boolean fromAir) {
        Object[] frames = jump.getKeyFrames();
        int first = fromAir ? Math.min(JUMP_AIR_FRAME, frames.length - 1) : 0;
        int count = frames.length - first;
        int index = first + Math.min(count - 1, (int) (progress * count));
        return (TextureRegion) frames[index];
    }

    public TextureRegion death(float time) {
        return death.getKeyFrame(time, false);
    }

    public float attackDuration() {
        return Math.max(0.4f, attack.get(Dir.SOUTH).getAnimationDuration());
    }

    public float deathDuration() {
        return Math.max(0.5f, death.getAnimationDuration());
    }

    /**
     * Textura de 1 pixel branco (olhos brilhando, etc.)
     */
    public Texture pixel() {
        return pixel;
    }

    public void dispose() {
        for (Texture t : textures) t.dispose();
        pixel.dispose();
    }
}
