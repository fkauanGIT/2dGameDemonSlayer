package app.evoMouse;

import app.evoMouse.player.Player;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import static com.badlogic.gdx.Gdx.input;

/**
 * {@code GameScreen} é a tela principal do jogo "EvoMouse".
 * <p>
 * Responsável por:
 * <ul>
 *   <li>Gerenciar e renderizar o cenário isométrico.</li>
 *   <li>Atualizar o estado do jogador ({@link Player}).</li>
 *   <li>Controlar a câmera e a viewport.</li>
 *   <li>Interpretar entradas do usuário (movimento e zoom).</li>
 * </ul>
 * <p>
 * Esta classe funciona como o loop principal de renderização do jogo.
 */
public class GameScreen extends ScreenAdapter {

    /**
     * Largura da viewport (em pixels).
     */
    public static final int WIDTH = 320 * 4;

    /**
     * Altura da viewport (em pixels).
     */
    public static final int HEIGHT = 180 * 4;

    private final SpriteBatch batch;
    private OrthographicCamera camera;
    private IsometricRenderer renderer;
    private Viewport viewport;
    private Player player;

    /**
     * Cria uma nova instância de {@code GameScreen}.
     *
     * @param batch o {@link SpriteBatch} responsável por desenhar os elementos na tela.
     */
    public GameScreen(SpriteBatch batch) {
        this.batch = batch;
    }

    /**
     * Inicializa os componentes principais da tela, incluindo:
     * <ul>
     *   <li>Câmera ortográfica centralizada.</li>
     *   <li>Viewport ajustável (FitViewport).</li>
     *   <li>Renderizador isométrico do mapa ({@link IsometricRenderer}).</li>
     *   <li>Instância do jogador ({@link Player}).</li>
     * </ul>
     */
    @Override
    public void show() {
        camera = new OrthographicCamera(WIDTH, HEIGHT);
        viewport = new FitViewport(WIDTH, HEIGHT, camera);
        camera.position.set(WIDTH / 2f - 500, HEIGHT / 2f, 10);

        renderer = new IsometricRenderer();
        player = new Player();
    }

    /**
     * Atualiza e renderiza o jogo a cada frame.
     * <p>
     * Este método:
     * <ul>
     *   <li>Limpa o buffer de tela.</li>
     *   <li>Processa a entrada do usuário.</li>
     *   <li>Atualiza o jogador e a câmera.</li>
     *   <li>Desenha o terreno e o jogador.</li>
     * </ul>
     *
     * @param delta tempo (em segundos) desde o último frame.
     */
    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(camera.combined);

        handleInput();
        player.update(delta, renderer.getObjectHitBoxes());
        camera.update();

        batch.begin();
        renderer.drawGround(batch, player);
        batch.end();

        renderer.renderHitboxes(camera, player);
    }

    /**
     * Processa os comandos de entrada do teclado.
     *
     * <p>Controles disponíveis:</p>
     * <ul>
     *   <li><b>Q</b> – Aproxima (zoom in)</li>
     *   <li><b>E</b> – Afasta (zoom out)</li>
     *   <li><b>Setas direcionais</b> – Move a câmera</li>
     * </ul>
     */
    private void handleInput() {
        if (input.isKeyPressed(Input.Keys.Q)) {
            camera.zoom -= 0.002f;
        } else if (input.isKeyPressed(Input.Keys.E)) {
            camera.zoom += 0.002f;
        }

        if (input.isKeyPressed(Input.Keys.LEFT)) {
            camera.position.x -= 1;
        } else if (input.isKeyPressed(Input.Keys.RIGHT)) {
            camera.position.x += 1;
        } else if (input.isKeyPressed(Input.Keys.UP)) {
            camera.position.y -= 1;
        } else if (input.isKeyPressed(Input.Keys.DOWN)) {
            camera.position.y += 1;
        }
    }

    /**
     * Libera recursos da tela quando ela é descartada.
     * <p>
     * Atualmente não implementa descarte manual, mas deve ser usado para:
     * <ul>
     *   <li>Descarregar texturas e sons.</li>
     *   <li>Fechar batchs e outros objetos gráficos.</li>
     * </ul>
     * </p>
     */
    @Override
    public void dispose() {
        // TODO: Liberar texturas e batchs aqui quando necessário
    }
}
