package app.evoMouse;

import app.evoMouse.factory.CombatSystem.CombatSystem;
import app.evoMouse.factory.EntitysObejcts.WorldObject;
import app.evoMouse.factory.EntitysObejcts.WorldObjectFactory;
import app.evoMouse.enemies.OniEnemy;
import app.evoMouse.enemies.OniSprites;
import app.evoMouse.objects.CampfireObject;
import app.evoMouse.objects.TreeObject;
import app.evoMouse.objects.TreeObject.TreeType;
import app.evoMouse.player.Clone;
import app.evoMouse.player.Entity;
import app.evoMouse.player.Player;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Classe responsável por renderizar o mapa isométrico e seus elementos no jogo EvoMouse.
 * <p>
 * A {@code IsometricRenderer} é a classe principal para:
 * - Gerar mapa procedural (terreno com relevo + objetos)
 * - Renderizar blocos, objetos e personagens respeitando profundidade e altura
 * - Gerenciar colisões e hitboxes
 * <p>
 * <b>Relevo:</b> cada tile tem um nível de altura (0 a {@link #MAX_LEVEL}). O nível 0 é
 * desenhado antes de tudo (nunca cobre ninguém). Os blocos acima dele formam "colunas"
 * que entram na mesma ordenação de profundidade dos objetos e personagens, para que um
 * morro na frente do jogador o cubra e um morro atrás não.
 */
public class IsometricRenderer implements Terrain {

    // === TEXTURAS ===
    private final Map<Integer, Texture> groundTextures = new HashMap<>();
    private final Map<Integer, Texture> objectTextures = new HashMap<>();
    private final Map<Integer, Texture> shadowTextures = new HashMap<>();
    private Texture dirtTexture;
    private Texture campfireTexture;

    // === OBJETOS DO MUNDO ===
    private final Array<WorldObject> worldObjects = new Array<>();
    private final Array<Rectangle> objectCollision = new Array<>();
    private final Array<Rectangle> objectHitbox = new Array<>();
    private final WorldObjectFactory worldObjectFactory = new WorldObjectFactory();

    // === MAPA ===
    private int[][] groundLayer;
    private int[][] heightLayer;
    private int[][] objectLayer;

    /**
     * Rampas/escadas: direção em que o tile sobe (0 = plano, ver {@link SlopeSprites}).
     * Um tile com rampa tem a base no seu nível e o topo no nível + 1
     */
    private int[][] slopeLayer;
    private boolean[][] stairsLayer;
    private SlopeSprites slopeSprites;

    // === RENDER ===
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final Array<DrawItem> drawItems = new Array<>();
    private final Array<DrawItem> drawItemPool = new Array<>();

    // === CONSTANTES ===
    public static final int TILE_WIDTH = 64;
    public static final int TILE_HEIGHT = 64;

    /**
     * Nível máximo do relevo
     */
    private static final int MAX_LEVEL = 2;

    /**
     * Quantos Oni ficam escondidos nas árvores
     */
    private static final int ONI_COUNT = 3;

    private OniSprites oniSprites;

    /**
     * Tile onde fica a fogueira (perto do ponto inicial do jogador)
     */
    private static final int CAMPFIRE_ROW = 2, CAMPFIRE_COL = 3;

    /**
     * Sombras: tamanho do pixel de arte, opacidade e largura da sombra do jogador
     */
    private static final float SHADOW_PIXEL = 2f;
    private static final float SHADOW_ALPHA = 0.3f;
    private static final float PLAYER_SHADOW_WIDTH = 26f;

    /**
     * Ordem de desenho em caso de empate de profundidade
     */
    private static final int ORDER_COLUMN = 0, ORDER_ENTITY = 1, ORDER_OBJECT = 2;

    /**
     * Algo a ser desenhado na passada ordenada: meia coluna de blocos, objeto ou entidade.
     */
    private static final class DrawItem {
        float key;
        int order;
        int row, col;
        /**
         * Metade da coluna: 0 = esquerda, 1 = direita
         */
        int half;
        WorldObject object;
        Entity entity;
    }

    /**
     * Metades esquerda/direita de cada textura de bloco (cache)
     */
    private final Map<Texture, TextureRegion[]> blockHalves = new HashMap<>();

    // === CONSTRUTOR ===
    public IsometricRenderer() {
        // Chão
        groundTextures.put(0, new Texture(Gdx.files.internal("assets/blocks/grass_2.png")));
        groundTextures.put(1, new Texture(Gdx.files.internal("assets/blocks/grass_1.png")));
        groundTextures.put(2, new Texture(Gdx.files.internal("assets/blocks/grass_3.png")));
        Pixmap dirt = createDirtPixmap("assets/blocks/grass_1.png");
        dirtTexture = new Texture(dirt);
        slopeSprites = new SlopeSprites("assets/blocks/grass_1.png", dirt);
        dirt.dispose();

        // Objetos
        objectTextures.put(1, new Texture(Gdx.files.internal(TreeType.PINE.texturePath)));
        objectTextures.put(2, new Texture(Gdx.files.internal(TreeType.SAKURA.texturePath)));
        objectTextures.put(3, new Texture(Gdx.files.internal(TreeType.CURSED.texturePath)));
        campfireTexture = new Texture(Gdx.files.internal("assets/landscape_elements/campfire.png"));
        oniSprites = new OniSprites();

        // Gera o primeiro mapa
        generateLayers();
    }

    // === RENDERIZAÇÃO PRINCIPAL ===
    public void drawGround(SpriteBatch batch, Player player) {
        // Regenerar mapa ao apertar G
        if (Gdx.input.isKeyJustPressed(Input.Keys.G)) {
            generateLayers();
            return;
        }

        // Camada 1 — nível 0 de todos os tiles (de trás para frente).
        // Tiles com blocos acima usam terra: a grama ficaria escondida e a borda verde viraria listra
        for (int row = groundLayer.length - 1; row >= 0; row--) {
            for (int col = groundLayer[row].length - 1; col >= 0; col--) {
                drawBlock(batch, row, col, 0);
            }
        }

        // Avança animações de morte e remove os objetos que já terminaram
        float delta = Gdx.graphics.getDeltaTime();
        for (WorldObject obj : worldObjects) {
            if (obj instanceof OniEnemy oni) oni.setTarget(player);
            obj.update(delta);
        }
        CombatSystem.removeFinished(worldObjects);

        // === CAMADA 2: COLUNAS DE RELEVO + OBJETOS + PERSONAGENS (ORDENADOS) ===
        collectDrawItems(player);

        for (DrawItem item : drawItems) {
            if (item.object != null) {
                WorldObject obj = item.object;
                if (obj.getShadowWidth() > 0) {
                    drawShadow(batch, obj.getBaseX(), obj.getBaseY() + obj.getZ(),
                            obj.getShadowWidth(), obj.getVisibility());
                }
                obj.render(batch);
            } else if (item.entity != null) {
                Entity entity = item.entity;
                float alpha = entity instanceof Clone clone ? clone.getOpacity() : 1f;
                // Sombra fica no chão e esmaece quanto mais alto o pulo
                float height = entity.getZ() - entity.getShadowZ();
                alpha *= 1f - Math.min(0.6f, height / 80f);
                drawShadow(batch, entity.getIsoX() + TILE_WIDTH / 2f + 1f,
                        entity.getIsoY() + Player.FEET_OFFSET_Y - 2f + entity.getShadowZ(), PLAYER_SHADOW_WIDTH, alpha);
                entity.render(batch);
            } else {
                int row = item.row, col = item.col;
                for (int level = 1; level <= heightLayer[row][col]; level++) {
                    drawHalf(batch, blockTexture(row, col, level), row, col, level, item.half);
                }
                if (slopeLayer[row][col] != 0) {
                    Texture wedge = slopeSprites.get(slopeLayer[row][col], stairsLayer[row][col]);
                    drawHalf(batch, wedge, row, col, heightLayer[row][col], item.half);
                }
            }
        }

        // Verifica ataques e remove objetos
        CombatSystem.handleAttacks(player, worldObjects, objectCollision, objectHitbox);
    }

    /**
     * Desenha um bloco do tile no nível informado. O bloco do topo é grama; os de baixo, terra.
     */
    private void drawBlock(SpriteBatch batch, int row, int col, int level) {
        Texture texture = blockTexture(row, col, level);
        if (texture != null) {
            batch.draw(texture, tileX(row, col), tileY(row, col) + level * LEVEL_HEIGHT, TILE_WIDTH, TILE_HEIGHT);
        }
    }

    /**
     * Desenha só a metade esquerda (0) ou direita (1) de uma textura de bloco (ou cunha
     * de rampa/escada, que é mais alta) apoiada no nível informado do tile.
     */
    private void drawHalf(SpriteBatch batch, Texture texture, int row, int col, int level, int half) {
        if (texture == null) return;

        TextureRegion region = blockHalves.computeIfAbsent(texture, t -> new TextureRegion[]{
                new TextureRegion(t, 0, 0, t.getWidth() / 2, t.getHeight()),
                new TextureRegion(t, t.getWidth() / 2, 0, t.getWidth() / 2, t.getHeight())
        })[half];

        // Mesma escala do bloco: 32px de arte -> 64px no mundo
        float scale = TILE_WIDTH / 32f;
        float halfWidth = TILE_WIDTH / 2f;
        batch.draw(region, tileX(row, col) + half * halfWidth, tileY(row, col) + level * LEVEL_HEIGHT,
                halfWidth, texture.getHeight() * scale);
    }

    /**
     * O bloco do topo é grama; os de baixo (e o que fica sob uma rampa) são terra.
     */
    private Texture blockTexture(int row, int col, int level) {
        boolean isTop = level == heightLayer[row][col] && slopeLayer[row][col] == 0;
        return isTop ? groundTextures.get(groundLayer[row][col]) : dirtTexture;
    }

    /**
     * Altura máxima do tile em níveis: a rampa conta como um nível a mais.
     */
    private int topLevel(int row, int col) {
        return heightLayer[row][col] + (slopeLayer[row][col] != 0 ? 1 : 0);
    }

    /**
     * Monta e ordena a lista de desenho: de trás (Y maior) para frente.
     */
    private void collectDrawItems(Player player) {
        drawItemPool.addAll(drawItems);
        drawItems.clear();

        // Cada coluna vira duas metades. A metade esquerda só encosta no vizinho de trás
        // à esquerda (row + 1); a direita, no de trás à direita (col + 1).
        // Se esse vizinho é tão alto quanto a coluna (platô), nada atrás dele fica escondido
        // por ela: a metade é desenhada mais cedo, para não cobrir os pés/raízes de quem está no platô.
        // Se o vizinho é mais baixo (penhasco), a metade usa o centro do tile e cobre quem está atrás.
        for (int row = 0; row < heightLayer.length; row++) {
            for (int col = 0; col < heightLayer[row].length; col++) {
                int height = topLevel(row, col);
                if (height == 0) continue;

                for (int half = 0; half < 2; half++) {
                    int backRow = half == 0 ? row + 1 : row;
                    int backCol = half == 0 ? col : col + 1;
                    boolean plateauBehind = backRow >= heightLayer.length || backCol >= heightLayer[0].length
                            || topLevel(backRow, backCol) >= height;

                    float key = centerY(row, col) + (plateauBehind ? TILE_HEIGHT / 4f : 0f);
                    DrawItem item = obtainItem(key, ORDER_COLUMN);
                    item.row = row;
                    item.col = col;
                    item.half = half;
                }
            }
        }

        for (WorldObject obj : worldObjects) {
            obtainItem(sortKey(obj.getBaseX(), obj.getBaseY()), ORDER_OBJECT).object = obj;
        }

        obtainItem(entityKey(player), ORDER_ENTITY).entity = player;
        for (Clone clone : player.getClones()) {
            obtainItem(entityKey(clone), ORDER_ENTITY).entity = clone;
        }

        drawItems.sort((a, b) -> a.key != b.key ? Float.compare(b.key, a.key) : Integer.compare(a.order, b.order));
    }

    private DrawItem obtainItem(float key, int order) {
        DrawItem item = drawItemPool.isEmpty() ? new DrawItem() : drawItemPool.pop();
        item.key = key;
        item.order = order;
        item.object = null;
        item.entity = null;
        drawItems.add(item);
        return item;
    }

    private float entityKey(Entity entity) {
        return sortKey(entity.getIsoX() + TILE_WIDTH / 2f, entity.getIsoY() + Player.FEET_OFFSET_Y);
    }

    /**
     * Profundidade de algo que toca o chão no ponto (groundX, groundY).
     * <p>
     * Em cima de um bloco elevado, a chave é limitada ao centro do tile, para que a coisa
     * seja sempre desenhada depois da coluna em que pisa (mesmo na metade de trás do tile).
     */
    private float sortKey(float groundX, float groundY) {
        int[] tile = tileAt(groundX, groundY);
        if (tile == null || topLevel(tile[0], tile[1]) == 0) return groundY;
        return Math.min(groundY, centerY(tile[0], tile[1]));
    }

    // === SOMBRAS ===

    private void drawShadow(SpriteBatch batch, float centerX, float centerY, float width, float alpha) {
        if (alpha <= 0f) return;
        Texture shadow = getShadowTexture(width);
        float w = shadow.getWidth() * SHADOW_PIXEL;
        float h = shadow.getHeight() * SHADOW_PIXEL;
        batch.setColor(0f, 0f, 0f, SHADOW_ALPHA * alpha);
        batch.draw(shadow, centerX - w / 2f, centerY - h / 2f, w, h);
        batch.setColor(Color.WHITE);
    }

    /**
     * Elipse branca sólida com metade da altura da largura (proporção do chão isométrico),
     * gerada em pixels de arte para combinar com o resto do jogo. Fica em cache por largura.
     */
    private Texture getShadowTexture(float worldWidth) {
        int artWidth = Math.max(4, Math.round(worldWidth / SHADOW_PIXEL));
        return shadowTextures.computeIfAbsent(artWidth, w -> {
            int h = Math.max(2, w / 2);
            Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.WHITE);
            float rx = w / 2f, ry = h / 2f;
            for (int py = 0; py < h; py++) {
                for (int px = 0; px < w; px++) {
                    float dx = (px + 0.5f - rx) / rx;
                    float dy = (py + 0.5f - ry) / ry;
                    if (dx * dx + dy * dy <= 1f) pixmap.drawPixel(px, py);
                }
            }
            Texture texture = new Texture(pixmap);
            pixmap.dispose();
            return texture;
        });
    }

    /**
     * Cria o bloco de terra a partir do bloco de grama: mantém o losango do topo
     * (que fica escondido sob o bloco de cima) e troca a faixa verde das laterais
     * pela cor da terra logo abaixo, para os penhascos não ficarem listrados.
     */
    private static Pixmap createDirtPixmap(String grassPath) {
        Pixmap pixmap = new Pixmap(Gdx.files.internal(grassPath));
        int w = pixmap.getWidth(), h = pixmap.getHeight();
        Color c = new Color();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // Losango do topo: largura w, altura w/2
                float dx = Math.abs(x + 0.5f - w / 2f) / (w / 2f);
                float dy = Math.abs(y + 0.5f - w / 4f) / (w / 4f);
                if (dx + dy <= 1f) continue;

                Color.rgba8888ToColor(c, pixmap.getPixel(x, y));
                if (c.a == 0 || !(c.g > c.r && c.g > c.b)) continue;

                // Procura a primeira cor de terra abaixo, na mesma coluna
                for (int below = y + 1; below < h; below++) {
                    int pixel = pixmap.getPixel(x, below);
                    Color.rgba8888ToColor(c, pixel);
                    if (c.a > 0 && !(c.g > c.r && c.g > c.b)) {
                        pixmap.drawPixel(x, y, pixel);
                        break;
                    }
                }
            }
        }
        return pixmap;
    }

    // === CRIAÇÃO DE OBJETOS ===
    private void createAndAddObject(int objectType, int row, int col) {
        if (groundLayer[row][col] != 1) return;

        Texture texture = objectTextures.get(objectType);
        float z = heightLayer[row][col] * LEVEL_HEIGHT;
        addObject(worldObjectFactory.create(objectType, tileX(row, col), tileY(row, col), z, texture));
    }

    private void addObject(WorldObject obj) {
        if (obj != null) {
            worldObjects.add(obj);
            objectCollision.add(obj.getBoundsCollision());
            objectHitbox.add(obj.getBoundsHitbox());
        }
    }

    /**
     * @return true se o tile está perto do ponto inicial do jogador ou da fogueira
     */
    private boolean isReserved(int row, int col) {
        boolean nearSpawn = row <= 1 && col <= 1;
        boolean nearCampfire = Math.abs(row - CAMPFIRE_ROW) <= 2 && Math.abs(col - CAMPFIRE_COL) <= 2;
        return nearSpawn || nearCampfire;
    }

    // === GERAÇÃO DO MAPA ===
    private void generateLayers() {
        Random r = new Random();
        int size = Math.max(10, r.nextInt(10) + 10);

        groundLayer = new int[size][size];
        heightLayer = generateHeights(size, r);
        objectLayer = new int[size][size];

        for (WorldObject obj : worldObjects) obj.dispose();
        worldObjects.clear();
        objectCollision.clear();
        objectHitbox.clear();

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                // Gera chão
                int g = r.nextInt(100);
                if (g < 30) groundLayer[row][col] = 0;
                else if (g < 70) groundLayer[row][col] = 1;
                else groundLayer[row][col] = 2;

                // Gera objetos
                int o = r.nextInt(100);
                if (o < 5) objectLayer[row][col] = 1;
                else if (o < 10) objectLayer[row][col] = 2;
                else if (o < 13) objectLayer[row][col] = 3;
                else objectLayer[row][col] = 0;

                // Ponto inicial e arredores da fogueira ficam livres e planos
                if (isReserved(row, col)) {
                    objectLayer[row][col] = 0;
                    heightLayer[row][col] = 0;
                }
            }
        }

        // Tile inicial com chão
        groundLayer[0][0] = 1;

        // Rampas e escadas ligando os platôs (limpam os objetos do caminho)
        placeSlopes(r);

        // Objetos só depois do relevo final (precisam saber a altura do tile)
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                createAndAddObject(objectLayer[row][col], row, col);
            }
        }

        // Fogueira do refúgio
        addObject(new CampfireObject(tileX(CAMPFIRE_ROW, CAMPFIRE_COL), tileY(CAMPFIRE_ROW, CAMPFIRE_COL),
                0f, campfireTexture));

        spawnOni(r);
    }

    /**
     * Esconde alguns Oni em árvores longe do refúgio.
     */
    private void spawnOni(Random r) {
        Array<TreeObject> candidates = new Array<>();
        for (WorldObject obj : worldObjects) {
            if (obj instanceof TreeObject tree) {
                // Distância do ponto inicial em tiles (aprox.): evita emboscada logo no começo
                float tiles = (Math.abs(tree.getBaseX()) / 32f + (tree.getBaseY() - 48f) / 16f) / 2f;
                if (tiles >= 5f) candidates.add(tree);
            }
        }
        candidates.shuffle();

        int count = Math.min(ONI_COUNT, candidates.size);
        for (int i = 0; i < count; i++) {
            OniEnemy oni = new OniEnemy(candidates.get(i), oniSprites, this, objectCollision, worldObjects);
            worldObjects.add(oni);
            objectHitbox.add(oni.getBoundsHitbox()); // não bloqueia o caminho: sem caixa de colisão
        }
    }

    /**
     * Liga cada platô ao nível de baixo com até 2 rampas ou escadas.
     * <p>
     * A rampa fica no tile mais baixo (L), encostada no platô (H), subindo na direção dele.
     * O tile de acesso do outro lado (A) precisa ser plano e da mesma altura de L,
     * e L, A e H ficam livres de árvores.
     */
    private void placeSlopes(Random r) {
        int n = heightLayer.length;
        slopeLayer = new int[n][n];
        stairsLayer = new boolean[n][n];

        // Rótulos dos platôs: tiles vizinhos com o mesmo nível (>= 1)
        int[][] region = new int[n][n];
        for (int[] line : region) java.util.Arrays.fill(line, -1);
        int regions = 0;
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                if (heightLayer[row][col] > 0 && region[row][col] < 0) floodRegion(region, row, col, regions++);
            }
        }

        // Direções: deslocamento de H para L e direção de subida da rampa em L
        int[][] offsets = {{0, -1}, {-1, 0}, {0, 1}, {1, 0}};
        int[] rises = {SlopeSprites.RISE_COL, SlopeSprites.RISE_ROW, SlopeSprites.RISE_NEG_COL, SlopeSprites.RISE_NEG_ROW};

        for (int id = 0; id < regions; id++) {
            Array<int[]> candidates = new Array<>();
            for (int row = 0; row < n; row++) {
                for (int col = 0; col < n; col++) {
                    if (region[row][col] != id) continue;
                    int h = heightLayer[row][col];
                    for (int d = 0; d < 4; d++) {
                        int lr = row + offsets[d][0], lc = col + offsets[d][1];
                        int ar = lr + offsets[d][0], ac = lc + offsets[d][1];
                        if (!inBounds(lr, lc) || !inBounds(ar, ac)) continue;
                        if (heightLayer[lr][lc] != h - 1 || heightLayer[ar][ac] != h - 1) continue;
                        if (slopeLayer[lr][lc] != 0 || slopeLayer[ar][ac] != 0) continue;
                        if (lr == CAMPFIRE_ROW && lc == CAMPFIRE_COL) continue;
                        candidates.add(new int[]{lr, lc, ar, ac, row, col, rises[d]});
                    }
                }
            }

            candidates.shuffle();
            int placed = 0;
            for (int[] c : candidates) {
                if (placed == 2) break;
                // Não deixa duas rampas coladas lado a lado
                if (slopeLayer[c[0]][c[1]] != 0 || hasSlopeNeighbor(c[0], c[1])) continue;

                slopeLayer[c[0]][c[1]] = c[6];
                stairsLayer[c[0]][c[1]] = r.nextBoolean();
                objectLayer[c[0]][c[1]] = 0;
                objectLayer[c[2]][c[3]] = 0;
                objectLayer[c[4]][c[5]] = 0;
                placed++;
            }
        }
    }

    private void floodRegion(int[][] region, int startRow, int startCol, int id) {
        int level = heightLayer[startRow][startCol];
        Array<int[]> stack = new Array<>();
        stack.add(new int[]{startRow, startCol});
        region[startRow][startCol] = id;
        while (!stack.isEmpty()) {
            int[] t = stack.pop();
            int[][] next = {{t[0] + 1, t[1]}, {t[0] - 1, t[1]}, {t[0], t[1] + 1}, {t[0], t[1] - 1}};
            for (int[] nb : next) {
                if (inBounds(nb[0], nb[1]) && region[nb[0]][nb[1]] < 0 && heightLayer[nb[0]][nb[1]] == level) {
                    region[nb[0]][nb[1]] = id;
                    stack.add(nb);
                }
            }
        }
    }

    private boolean hasSlopeNeighbor(int row, int col) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if ((dr != 0 || dc != 0) && inBounds(row + dr, col + dc) && slopeLayer[row + dr][col + dc] != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean inBounds(int row, int col) {
        return row >= 0 && col >= 0 && row < heightLayer.length && col < heightLayer[0].length;
    }

    /**
     * Relevo por "value noise": valores aleatórios numa grade grossa, interpolados
     * suavemente entre si. Gera platôs largos separados por bastante chão plano
     * (espaço para lutar), em vez de muitos morros pequenos.
     */
    private static int[][] generateHeights(int size, Random r) {
        int cell = 6; // distância (em tiles) entre os pontos aleatórios: maior = morros mais largos
        int gridSize = size / cell + 2;
        float[][] grid = new float[gridSize][gridSize];
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                grid[i][j] = r.nextFloat();
            }
        }

        int[][] heights = new int[size][size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                float fy = row / (float) cell, fx = col / (float) cell;
                int iy = (int) fy, ix = (int) fx;
                float ty = smooth(fy - iy), tx = smooth(fx - ix);

                float top = MathUtils.lerp(grid[iy][ix], grid[iy][ix + 1], tx);
                float bottom = MathUtils.lerp(grid[iy + 1][ix], grid[iy + 1][ix + 1], tx);
                float n = MathUtils.lerp(top, bottom, ty);

                // Maioria plana; platôs de 1 nível e, mais raros, de 2
                int level;
                if (n < 0.6f) level = 0;
                else if (n < 0.8f) level = 1;
                else level = MAX_LEVEL;
                heights[row][col] = level;
            }
        }
        return heights;
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    // === COORDENADAS ===

    private static float tileX(int row, int col) {
        return (col - row) * (TILE_WIDTH / 2f);
    }

    private static float tileY(int row, int col) {
        return (col + row) * (TILE_HEIGHT / 4f);
    }

    /**
     * Centro da face superior do tile, no plano do chão (nível 0)
     */
    private static float centerY(int row, int col) {
        return tileY(row, col) + TILE_HEIGHT * 0.75f;
    }

    /**
     * Converte um ponto do chão para (row, col) com margem: fora do losango do mapa
     * (recolhido um pouco para o sprite não "pendurar" na borda) devolve null.
     * <p>
     * Inverte a projeção isométrica: x = (col - row) * W/2, y = (col + row) * H/4.
     */
    private int[] tileAt(float groundX, float groundY) {
        float[] grid = gridAt(groundX, groundY);
        if (grid == null) return null;
        return new int[]{
                MathUtils.clamp(Math.round(grid[0]), 0, heightLayer.length - 1),
                MathUtils.clamp(Math.round(grid[1]), 0, heightLayer[0].length - 1)
        };
    }

    /**
     * Mesma conversão de {@link #tileAt}, mas devolve (row, col) fracionários.
     */
    private float[] gridAt(float groundX, float groundY) {
        float u = (groundX - TILE_WIDTH / 2f) / (TILE_WIDTH / 2f);        // col - row
        float v = (groundY - TILE_HEIGHT * 0.75f) / (TILE_HEIGHT / 4f);   // col + row

        float col = (u + v) / 2f;
        float row = (v - u) / 2f;

        float min = -0.45f;
        float maxRow = heightLayer.length - 0.55f;
        float maxCol = heightLayer[0].length - 0.55f;
        if (row < min || row > maxRow || col < min || col > maxCol) return null;
        return new float[]{row, col};
    }

    /**
     * Altura do chão sob os pés de um personagem (com o sprite do jogador), ou -1 fora do mapa.
     */
    @Override
    public float surfaceAt(float isoX, float isoY) {
        return groundSurface(isoX + TILE_WIDTH / 2f, isoY + Player.FEET_OFFSET_Y);
    }

    /**
     * Altura do chão (em níveis) num ponto do plano: o nível do tile, mais a subida
     * da rampa/escada se houver. -1 fora do mapa.
     */
    private float groundSurface(float groundX, float groundY) {
        float[] grid = gridAt(groundX, groundY);
        if (grid == null) return -1f;

        int row = MathUtils.clamp(Math.round(grid[0]), 0, heightLayer.length - 1);
        int col = MathUtils.clamp(Math.round(grid[1]), 0, heightLayer[0].length - 1);
        float height = heightLayer[row][col];

        int dir = slopeLayer[row][col];
        if (dir != 0) {
            height += MathUtils.clamp(SlopeSprites.rise(dir, grid[0] - row, grid[1] - col), 0f, 1f);
        }
        return height;
    }

    /**
     * Altura do chão (em pixels) num ponto qualquer do plano; 0 fora do mapa.
     */
    private float groundZ(float groundX, float groundY) {
        return Math.max(0f, groundSurface(groundX, groundY)) * LEVEL_HEIGHT;
    }

    // === DEBUG ===

    /**
     * Desenha as caixas de colisão (branco) e de hitbox (vermelho), elevadas à altura do chão.
     */
    public void renderDebug(OrthographicCamera camera, Player player) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        shapeRenderer.setColor(Color.WHITE);
        for (Rectangle rect : objectCollision) drawDebugRect(rect, groundZ(rect.x + rect.width / 2f, rect.y + rect.height / 2f));

        shapeRenderer.setColor(Color.RED);
        for (Rectangle rect : objectHitbox) drawDebugRect(rect, groundZ(rect.x + rect.width / 2f, rect.y + rect.height / 2f));
        drawDebugRect(player.getBounds(), player.getZ());

        shapeRenderer.end();
    }

    private void drawDebugRect(Rectangle rect, float z) {
        shapeRenderer.rect(rect.x, rect.y + z, rect.width, rect.height);
    }

    // === GETTERS ===
    public Array<Rectangle> getObjectCollision() {
        return objectCollision;
    }

    public Array<WorldObject> getWorldObjects() {
        return worldObjects;
    }

    public Array<Rectangle> getObjectHitbox() {
        return objectHitbox;
    }

    // === LIMPEZA ===
    public void dispose() {
        for (Texture t : groundTextures.values()) t.dispose();
        for (Texture t : objectTextures.values()) t.dispose();
        for (Texture t : shadowTextures.values()) t.dispose();
        dirtTexture.dispose();
        campfireTexture.dispose();
        slopeSprites.dispose();
        oniSprites.dispose();
        for (WorldObject obj : worldObjects) obj.dispose();
        shapeRenderer.dispose();
    }
}
