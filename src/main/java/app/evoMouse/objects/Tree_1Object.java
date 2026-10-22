package app.evoMouse.objects;

import app.evoMouse.factory.EntitysObejcts.WorldObject;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

import static app.evoMouse.IsometricRenderer.TILE_HEIGHT;

/**
 * Representa um objeto de árvore tipo 1 (Tree_1) no mapa.
 * Agora separa a posição base (para sorting/colisão) da posição de renderização (com offset).
 */
public class Tree_1Object extends WorldObject {

    // --- Propriedades de Posição e Estado ---
    private final float baseIsoX; // Posição X do tile (para sorting e colisão)
    private final float baseIsoY; // Posição Y do tile (para Z-sorting)

    private final float width;
    private final float height;

    // Offset de renderização aplicado para a árvore ficar com a base no tile
    private static final float RENDER_Y_OFFSET = TILE_HEIGHT / 1.5f;

    private final Rectangle boundsCollision;
    private final Rectangle boundsHitbox;
    private final Texture texture;

    // Constantes (Ajuste conforme o tamanho que você quer que o objeto apareça)
    private static final float RENDER_WIDTH = 64f;
    private static final float RENDER_HEIGHT = 94f;

    public Tree_1Object(float x, float y, Texture texture) {
        this.texture = texture;
        this.hp = 100;
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        // Armazena a posição base do tile (essencial para o Z-sorting)
        this.baseIsoX = x;
        this.baseIsoY = y;
        this.width = RENDER_WIDTH;
        this.height = RENDER_HEIGHT;

        // Inicializa o retângulo de limites (colisão e hitbox) usando a posição BASE (baseIsoX/Y)
        // Isso é crucial, pois as colisões devem ocorrer no chão do tile, e não no topo da árvore.
        this.boundsCollision = new Rectangle(baseIsoX + RENDER_WIDTH / 3f, baseIsoY + 42f, 23f, 30f);
        this.boundsHitbox = new Rectangle(baseIsoX + RENDER_HEIGHT / 5f, baseIsoY + 20f, 30f, 60f);
    }

    /**
     * Desenha o objeto no mapa, aplicando o offset de renderização vertical.
     *
     * @param batch O SpriteBatch usado para desenhar texturas.
     */
    @Override
    protected void renderAlive(SpriteBatch batch) {
        // Renderiza usando a posição base e aplica o offset de renderização (RENDER_Y_OFFSET)
        batch.draw(texture, baseIsoX, baseIsoY + RENDER_Y_OFFSET, width, height);
    }

    // --- Getters para Z-Sorting e Colisão ---

    /**
     * Retorna a posição X base do tile (Usada para Colisão e Câmera).
     */
    public float getX() {
        return baseIsoX;
    }

    /**
     * Retorna a posição Y base do tile (CRUCIAL para o Z-sorting).
     */
    public float getY() {
        return baseIsoY;
    }

    /**
     * Retorna o tipo de objeto (Tipo 1 para Tree_1).
     */
    public int getObjectType() {
        return 1;
    }

    public Rectangle getBoundsCollision() {
        return boundsCollision;
    }

    public Rectangle getBoundsHitbox() {
        return boundsHitbox;
    }

    public void updatePosition(float newX, float newY) {
        // Objeto de cenário geralmente não se move, mas se mover, precisa atualizar a base e os bounds
        // NOTA: Se o objeto for móvel, você precisaria atualizar baseIsoX/Y.

        // Atualiza a posição da hitbox (mantendo o offset interno)
        this.boundsCollision.setPosition(newX + RENDER_WIDTH / 3f, newY + 50f);
        this.boundsHitbox.setPosition(newX + RENDER_HEIGHT / 5f, newY + 40f);
    }

    @Override
    protected void onDeath() {
        System.out.println("Tree destroyed!");
    }

    public void dispose() {
        // Nada a liberar, pois a textura é gerenciada externamente.
    }
}