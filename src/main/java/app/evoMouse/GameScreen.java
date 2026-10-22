package app.evoMouse;

import app.evoMouse.factory.EntitysObejcts.WorldObject;
import app.evoMouse.lighting.Light;
import app.evoMouse.lighting.LightSystem;
import app.evoMouse.player.Clone;
import app.evoMouse.player.Player;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
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
    private ShapeRenderer hudRenderer;
    private Matrix4 hudMatrix;
    private LightSystem lightSystem;
    private Light playerLight;

    /**
     * Mostra as caixas de colisão/hitbox (alternado com F3)
     */
    private boolean debug = false;

    /**
     * Zoom inicial e limites (Q/E); valores menores = mais perto
     */
    private static final float DEFAULT_ZOOM = 0.75f;
    private static final float MIN_ZOOM = 0.3f, MAX_ZOOM = 1.5f;

    /**
     * Quão rápido a câmera alcança o jogador (maior = mais colada)
     */
    private static final float CAMERA_FOLLOW_SPEED = 6f;

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
        camera.zoom = DEFAULT_ZOOM;

        renderer = new IsometricRenderer();
        player = new Player();
        player.setTerrain(renderer);

        // Começa já centrada no jogador
        camera.position.set(playerCenterX(), playerCenterY(), 0);

        hudRenderer = new ShapeRenderer();
        hudMatrix = new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT);

        lightSystem = new LightSystem(WIDTH, HEIGHT);
        playerLight = new Light(0, 0, 160f, new Color(1f, 0.75f, 0.45f, 1f), 0.05f);
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

        handleInput();
        player.update(delta, renderer.getObjectCollision());
        followPlayer(delta);
        camera.update();
        batch.setProjectionMatrix(camera.combined); // depois de mover a câmera, para não atrasar 1 frame

        batch.begin();
        renderer.drawGround(batch, player);
        batch.end();

        renderLighting(delta);

        if (debug) renderer.renderDebug(camera, player);

        renderHud();
    }

    /**
     * Move a câmera suavemente até o jogador (incluindo a altura do terreno).
     */
    private void followPlayer(float delta) {
        float targetX = playerCenterX();
        float targetY = playerCenterY();
        float t = 1f - (float) Math.exp(-CAMERA_FOLLOW_SPEED * delta);
        camera.position.x += (targetX - camera.position.x) * t;
        camera.position.y += (targetY - camera.position.y) * t;
    }

    /**
     * Centro do corpo do jogador na tela (pés + altura do terreno + meia altura do sprite).
     */
    private float playerCenterX() {
        return player.getIsoX() + IsometricRenderer.TILE_WIDTH / 2f;
    }

    private float playerCenterY() {
        // Altura do chão (não a do pulo), para a câmera não balançar a cada pulo
        return player.getIsoY() + Player.FEET_OFFSET_Y + player.getShadowZ() + 20f;
    }

    /**
     * Avança o ciclo dia/noite, registra as luzes do frame e escurece a cena.
     * <p>
     * Segurar <b>T</b> acelera o tempo (para testar a noite).
     */
    private void renderLighting(float delta) {
        lightSystem.setTimeScale(input.isKeyPressed(Input.Keys.T) ? 40f : 1f);
        lightSystem.update(delta);

        // Lanterna do jogador, na altura do corpo
        playerLight.x = player.getIsoX() + IsometricRenderer.TILE_WIDTH / 2f;
        playerLight.y = player.getIsoY() + player.getZ() + 60f;
        lightSystem.addLight(playerLight);

        Light slashLight = player.getWaterSlash().getLight();
        if (slashLight != null) lightSystem.addLight(slashLight);

        for (Clone clone : player.getClones()) {
            Light light = clone.getLight();
            if (light != null) lightSystem.addLight(light);
        }

        for (WorldObject obj : renderer.getWorldObjects()) {
            Light light = obj.getLight();
            if (light != null) lightSystem.addLight(light);
        }

        lightSystem.render(batch, camera, delta);
    }

    /**
     * Desenha a barra de Ki e os selos digitados no canto da tela.
     */
    private void renderHud() {
        hudRenderer.setProjectionMatrix(hudMatrix);
        hudRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float barX = 20, barW = 200, barH = 14;

        // Vida
        float hpY = HEIGHT - 40;
        hudRenderer.setColor(0.2f, 0.12f, 0.12f, 1f);
        hudRenderer.rect(barX, hpY, barW, barH);
        hudRenderer.setColor(0.85f, 0.15f, 0.2f, 1f);
        hudRenderer.rect(barX, hpY, barW * (player.getHp() / (float) player.getMaxHp()), barH);

        // Ki
        float barY = hpY - 20;
        hudRenderer.setColor(0.15f, 0.15f, 0.2f, 1f);
        hudRenderer.rect(barX, barY, barW, barH);
        hudRenderer.setColor(player.isKiFull() ? Color.CYAN : Color.ROYAL);
        hudRenderer.rect(barX, barY, barW * (player.getKi() / player.getMaxKi()), barH);

        // Um quadrado por selo digitado na postura atual
        int sealCount = player.getSealSystem().getBuffer().length();
        hudRenderer.setColor(Color.GOLD);
        for (int i = 0; i < sealCount; i++) {
            hudRenderer.rect(barX + i * 18, barY - 22, 12, 12);
        }

        // Indicador de dia/noite: sol amarelo ou lua azulada no canto direito
        hudRenderer.setColor(lightSystem.isNight() ? new Color(0.75f, 0.85f, 1f, 1f) : Color.GOLD);
        hudRenderer.circle(WIDTH - 30, HEIGHT - 30, 12);

        hudRenderer.end();
    }

    /**
     * Processa os comandos de entrada do teclado.
     *
     * <p>Controles disponíveis:</p>
     * <ul>
     *   <li><b>Q</b> – Aproxima (zoom in)</li>
     *   <li><b>E</b> – Afasta (zoom out)</li>
     *   <li><b>F3</b> – Liga/desliga as caixas de colisão (debug)</li>
     * </ul>
     * A câmera segue o jogador automaticamente.
     */
    private void handleInput() {
        // Na postura de selo, Q/E são selos e não zoom
        boolean inStance = player.getSealSystem().isInStance();
        if (!inStance && input.isKeyPressed(Input.Keys.Q)) {
            camera.zoom -= 0.004f;
        } else if (!inStance && input.isKeyPressed(Input.Keys.E)) {
            camera.zoom += 0.004f;
        }
        camera.zoom = MathUtils.clamp(camera.zoom, MIN_ZOOM, MAX_ZOOM);

        if (input.isKeyJustPressed(Input.Keys.F3)) {
            debug = !debug;
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
        if (lightSystem != null) lightSystem.dispose();
        if (hudRenderer != null) hudRenderer.dispose();
    }
}
