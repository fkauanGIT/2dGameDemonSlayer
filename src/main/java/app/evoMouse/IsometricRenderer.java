package app.evoMouse;

import app.evoMouse.player.Player;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.util.Random;

/**
 * Classe responsável por renderizar o mapa isométrico e seus elementos no jogo EvoMouse.
 *
 * <p>
 * A {@code IsometricRenderer} é a classe principal para:
 * <ul>
 *   <li>Gerar um mapa procedural (matriz de inteiros) representando os tiles do terreno;</li>
 *   <li>Desenhar o terreno (camada de chão) e os objetos do cenário (camada superior);</li>
 *   <li>Renderizar o jogador respeitando a profundidade e sobreposição isométrica;</li>
 *   <li>Regenerar o mapa dinamicamente quando o jogador pressiona {@code G}.</li>
 * </ul>
 * </p>
 *
 * <p>
 * Cada tile do mapa é representado por um número inteiro que define seu tipo:
 * <ul>
 *   <li>0 → {@code grass_2}</li>
 *   <li>1 → {@code grass}</li>
 *   <li>2 → {@code tree_1}</li>
 *   <li>3 → {@code tree_2}</li>
 *   <li>4 → {@code tronco}</li>
 *   <li>5 → {@code grass_3}</li>
 * </ul>
 * </p>
 *
 * <p>
 * A renderização é feita em ordem decrescente de linhas e colunas para garantir a
 * sobreposição correta (profundidade visual).
 * </p>
 *
 * @author
 * @version 1.2
 * @since 2025
 */
public class IsometricRenderer {

    /**
     * Mapa bidimensional da camada de chão.
     * Cada célula contém um inteiro que representa o tipo do tile (grama, areia, etc.).
     */
    private int[][] groundLayer;

    /**
     * Mapa bidimensional da camada de objetos.
     * Cada célula contém um inteiro que representa um objeto (árvore, tronco, etc.).
     */
    private int[][] objectLayer;

    /**
     * Largura padrão de cada tile (em pixels).
     * Define a base horizontal de cada losango isométrico.
     */
    public static final int TILE_WIDTH = 64;

    /**
     * Altura padrão de cada tile (em pixels).
     * Define a altura vertical do losango isométrico.
     */
    public static final int TILE_HEIGHT = 64;

    /**
     * Texturas utilizadas para o terreno e os objetos do cenário.
     */
    public Texture grass, grass_2, grass_3, tree_1, tree_2, tronco;

    /**
     * Construtor da classe {@code IsometricRenderer}.
     *
     * <p>
     * Responsável por carregar todas as texturas do jogo e
     * gerar automaticamente um mapa procedural inicial.
     * </p>
     */
    public IsometricRenderer() {
        // Carrega texturas do terreno
        grass = new Texture(Gdx.files.internal("assets/blocks/grass_1.png"));
        grass_2 = new Texture(Gdx.files.internal("assets/blocks/grass_2.png"));
        grass_3 = new Texture(Gdx.files.internal("assets/blocks/grass_3.png"));

        // Carrega texturas dos objetos
        tree_1 = new Texture(Gdx.files.internal("assets/landscape_elements/tree-1.png"));
        tree_2 = new Texture(Gdx.files.internal("assets/landscape_elements/tree-2.png"));
        tronco = new Texture(Gdx.files.internal("assets/landscape_elements/tronco.png"));

        // Cria o primeiro mapa procedural
        generateLayers();
    }

    /**
     * Renderiza o mapa completo e o jogador no contexto isométrico.
     *
     * <p>
     * Este método:
     * <ul>
     *   <li>Desenha o chão (camada 1);</li>
     *   <li>Desenha os objetos do cenário (camada 2);</li>
     *   <li>Posiciona o jogador corretamente entre os elementos de acordo com a profundidade;</li>
     *   <li>Permite regenerar o mapa com a tecla {@code G}.</li>
     * </ul>
     * </p>
     *
     * @param batch  {@link SpriteBatch} usado para desenhar texturas.
     * @param player instância do jogador atual, usada para renderizar na posição correta.
     */
    public void drawGround(SpriteBatch batch, Player player) {

        // === CAMADA 1: CHÃO ===
        for (int row = groundLayer.length - 1; row >= 0; row--) {
            for (int col = groundLayer.length - 1; col >= 0; col--) {

                // Converte coordenadas matriciais para coordenadas isométricas
                float x = (col - row) * (TILE_WIDTH / 2f);
                float y = (col + row) * (TILE_HEIGHT / 4f);

                // Escolhe e desenha o tipo de chão
                switch (groundLayer[row][col]) {
                    case 0 -> batch.draw(grass_2, x, y, TILE_WIDTH, TILE_HEIGHT);
                    case 1 -> batch.draw(grass, x, y, TILE_WIDTH, TILE_HEIGHT);
                    case 2 -> batch.draw(grass_3, x, y, TILE_WIDTH, TILE_HEIGHT);
                }
            }
        }

        // === CAMADA 2: OBJETOS + JOGADOR ===
        for (int row = objectLayer.length - 1; row >= 0; row--) {
            for (int col = objectLayer[row].length - 1; col >= 0; col--) {

                // Converte novamente para coordenadas isométricas
                float x = (col - row) * (TILE_WIDTH / 2f);
                float y = (col + row) * (TILE_HEIGHT / 4f);

                // Identifica o tipo de chão (usado para compatibilidade com o tipo de objeto)
                int groundType = groundLayer[row][col];

                // Se o chão for grama, desenha objetos sobre ele
                if (groundType == 1) {
                    switch (objectLayer[row][col]) {
                        case 1 -> batch.draw(tree_1, x, y + TILE_HEIGHT / 1.5f, TILE_WIDTH, TILE_HEIGHT + 30f);
                        case 2 -> batch.draw(tree_2, x, y + TILE_HEIGHT / 1.6f, TILE_WIDTH, TILE_HEIGHT + 20f);
                        case 3 -> batch.draw(tronco, x, y + TILE_HEIGHT / 1.5f, TILE_WIDTH, TILE_HEIGHT / 2f);
                    }
                }

                // Verifica se o jogador deve ser desenhado neste ponto do mapa
                if (shouldRenderPlayerHere(player, x, y)) {
                    player.render(batch);
                }
            }
        }

        // Permite gerar um novo mapa quando a tecla 'G' é pressionada
        if (Gdx.input.isKeyJustPressed(Input.Keys.G)) {
            generateLayers();
        }
    }

    /**
     * Determina se o jogador deve ser renderizado sobre o tile atual.
     *
     * <p>
     * Essa verificação garante que o jogador seja desenhado no momento correto
     * da varredura do mapa, respeitando a profundidade (Y) do cenário.
     * </p>
     *
     * @param player jogador atual.
     * @param tileX  coordenada X do tile em isometria.
     * @param tileY  coordenada Y do tile em isometria.
     * @return {@code true} se o jogador deve ser desenhado neste ponto; caso contrário, {@code false}.
     */
    private boolean shouldRenderPlayerHere(Player player, float tileX, float tileY) {
        return Math.abs(player.getIsoX() - tileX) < TILE_WIDTH / 2f &&
                Math.abs(player.getIsoY() - tileY) < TILE_HEIGHT / 4f;
    }

    /**
     * Gera aleatoriamente as camadas de chão e objetos do mapa.
     *
     * <p>
     * O método cria duas matrizes bidimensionais de mesmo tamanho:
     * <ul>
     *   <li>{@code groundLayer} → define o tipo do chão em cada tile;</li>
     *   <li>{@code objectLayer} → define o tipo de objeto (ou nenhum) em cada tile.</li>
     * </ul>
     * </p>
     *
     * <p>
     * Cada célula é definida por sorteio, com probabilidades distintas para
     * cada tipo de elemento, garantindo variedade visual no mapa.
     * </p>
     */
    private void generateLayers() {
        Random r = new Random();

        // Define o tamanho do mapa entre 10x10 tiles
        int size = Math.max(10, r.nextInt(10));

        // Inicializa as matrizes
        groundLayer = new int[size][size];
        objectLayer = new int[size][size];

        // Preenche cada célula
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {

                // --- Camada de chão ---
                int groundRand = r.nextInt(100);
                if (groundRand < 30) groundLayer[row][col] = 0;      // grass_2
                else if (groundRand < 70) groundLayer[row][col] = 1; // grass
                else groundLayer[row][col] = 2;                      // grass_3

                // --- Camada de objetos ---
                int objRand = r.nextInt(100);
                if (objRand < 5) objectLayer[row][col] = 1;      // tree_1
                else if (objRand < 10) objectLayer[row][col] = 2; // tree_2
                else if (objRand < 13) objectLayer[row][col] = 3; // tronco
                else objectLayer[row][col] = 0;                   // vazio
            }
        }

        // Garante que o tile inicial [0][0] seja um terreno limpo (sem objeto)
        groundLayer[0][0] = 1;
        objectLayer[0][0] = 0;
    }

    /**
     * Libera os recursos gráficos (texturas) da memória.
     *
     * <p>
     * Deve ser chamado quando a tela ou jogo for encerrado,
     * evitando vazamentos de memória (memory leaks).
     * </p>
     */
    public void dispose() {
        grass.dispose();
        grass_2.dispose();
        grass_3.dispose();
        tree_1.dispose();
        tree_2.dispose();
        tronco.dispose();
    }
}
