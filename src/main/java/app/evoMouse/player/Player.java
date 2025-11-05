package app.evoMouse.player;

import app.evoMouse.IsometricRenderer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

import static app.evoMouse.IsometricRenderer.TILE_HEIGHT;
import static app.evoMouse.IsometricRenderer.TILE_WIDTH;

/**
 * Representa o jogador controlável na cena isométrica do jogo.
 * <p>
 * Responsável por:
 * <ul>
 * <li>Gerenciar a posição e direção do personagem</li>
 * <li>Processar entrada de teclado (WASD e X)</li>
 * <li>Executar e alternar entre animações de movimento, idle e ataque</li>
 * <li>Renderizar o sprite corretamente ajustado ao tile isométrico</li>
 * </ul>
 */
public class Player implements Entity {

    // ============================================================
    // === Campos de animação ====================================
    // ============================================================

    /**
     * Animações de caminhada nas quatro direções
     */
    private final Animation<TextureRegion> walkUp, walkDown, walkLeft, walkRight;

    /**
     * Animações de idle (parado) nas quatro direções
     */
    private final Animation<TextureRegion> idleUp, idleDown, idleLeft, idleRight;

    /**
     * Animações de ataque (primeiro golpe) nas quatro direções
     */
    private final Animation<TextureRegion> attackUpOne, attackDownOne, attackLeftOne, attackRightOne;

    /**
     * Animações de ataque (segundo golpe do combo) nas quatro direções
     */
    private final Animation<TextureRegion> attackUpTwo, attackDownTwo, attackLeftTwo, attackRightTwo;

    /**
     * Direção atual para a qual o jogador está olhando
     */
    private Direction facing = Direction.DOWN;

    /**
     * Enumeração de direções possíveis que o jogador pode estar voltado.
     */
    private enum Direction {
        UP, DOWN, LEFT, RIGHT
    }

    // ============================================================
    // === Controle de ataque e combo =============================
    // ============================================================

    /**
     * Indica se o jogador está executando uma animação de ataque
     */
    private boolean isAttacking = false;

    /**
     * Estágio atual do ataque: 0 = nenhum, 1 = primeiro ataque, 2 = segundo ataque
     */
    private int attackStage = 0;

    /**
     * Tempo decorrido desde o início ou final do último ataque
     */
    private float attackTimer = 0f;

    /**
     * Tempo limite (em segundos) para permitir o segundo ataque em sequência (combo)
     */
    private final float comboWindow = 0.4f;

    // ============================================================
    // === Controle geral de animação e posição ===================
    // ============================================================

    /**
     * Tempo acumulado de execução da animação atual
     */
    private float stateTime;

    /**
     * Animação atualmente em execução (idle, movimento ou ataque)
     */
    private Animation<TextureRegion> currentAnimation;

    /**
     * Indica se o jogador está se movendo no frame atual
     */
    private boolean moving;

    /**
     * Posição do jogador no mundo isométrico (coordenadas X e Y)
     */
    private final Vector2 pos;

    /**
     * Velocidade de deslocamento do jogador em pixels por frame
     */
    private final float speed = 2f;

    // ============================================================
    // === Construtor =============================================
    // ============================================================

    /**
     * Inicializa o jogador na posição (0,0), define animações e estado inicial.
     * <p>
     * Todas as animações são carregadas a partir dos diretórios dentro de
     * <code>assets/sprite_player</code>, seguindo a convenção:
     * <ul>
     * <li><b>walk/</b> — Animações de movimento</li>
     * <li><b>idle/</b> — Animações paradas</li>
     * <li><b>attack/one</b> — Primeiro golpe</li>
     * <li><b>attack/two</b> — Segundo golpe</li>
     * </ul>
     */
    public Player() {
        pos = new Vector2(0, 0);
        stateTime = 0f;

        // Carrega animações de movimento
        walkUp = loadAnimation("assets/sprite_player/up/walk", 7, 0.1f);
        walkDown = loadAnimation("assets/sprite_player/down/walk", 7, 0.1f);
        walkLeft = loadAnimation("assets/sprite_player/left/walk", 7, 0.1f);
        walkRight = loadAnimation("assets/sprite_player/right/walk", 7, 0.1f);

        // Carrega animações idle
        idleUp = loadAnimation("assets/sprite_player/up/idle", 7, 0.1f);
        idleDown = loadAnimation("assets/sprite_player/down/idle", 7, 0.1f);
        idleLeft = loadAnimation("assets/sprite_player/left/idle", 7, 0.1f);
        idleRight = loadAnimation("assets/sprite_player/right/idle", 7, 0.1f);

        // Carrega ataques (primeiro e segundo estágio)
        attackUpOne = loadAnimation("assets/sprite_player/up/attack/one", 7, 0.1f);
        attackDownOne = loadAnimation("assets/sprite_player/down/attack/one", 7, 0.1f);
        attackLeftOne = loadAnimation("assets/sprite_player/left/attack/one", 7, 0.1f);
        attackRightOne = loadAnimation("assets/sprite_player/right/attack/one", 7, 0.1f);

        attackUpTwo = loadAnimation("assets/sprite_player/up/attack/two", 7, 0.1f);
        attackDownTwo = loadAnimation("assets/sprite_player/down/attack/two", 7, 0.1f);
        attackLeftTwo = loadAnimation("assets/sprite_player/left/attack/two", 7, 0.1f);
        attackRightTwo = loadAnimation("assets/sprite_player/right/attack/two", 7, 0.1f);

        // Define animação inicial
        currentAnimation = idleDown;
    }

    /**
     * Calcula e retorna o retângulo de colisão (hitbox) do jogador.
     * <p>
     * A hitbox é uma pequena área na base do sprite, que define o ponto de contato
     * real do jogador com os objetos do cenário, permitindo a sensação de profundidade
     * em um ambiente isométrico.
     *
     * @return {@link Rectangle} representando a área de colisão do jogador.
     */
    public Rectangle getBounds() {
        // A base da hitbox deve estar levemente à frente da posição real do sprite,
        // e ser proporcional à largura dos pés
        float hitboxWidth = 20f;     // metade do tile para a largura
        float hitboxHeight = 10f;   // um quarto do tile para a altura
        float hitboxX = getIsoX() + TILE_WIDTH / 2.7f;  // centraliza na metade do tile
        float hitboxY = getIsoY() + TILE_HEIGHT / 2f; // assume pos.y está na base

        return new Rectangle(hitboxX, hitboxY, hitboxWidth, hitboxHeight);
    }

    // ============================================================
    // === Atualização por frame =================================
    // ============================================================

    /**
     * Atualiza o estado do jogador a cada frame.
     * <p>
     * Processa entrada do teclado, altera animações e calcula o deslocamento
     * no plano isométrico. Também gerencia o ciclo de ataque e combos.
     *
     * @param delta tempo (em segundos) desde o último frame
     * @param objectHitBoxes coleçao de hitboxes de objetos no mapa para verificao de colisão
     */
    @Override
    public void update(float delta, Array<Rectangle> objectHitBoxes) {
        moving = false;
        Vector2 direction = new Vector2();

        // --- Controle de ataque ativo ---
        if (isAttacking) {
            attackTimer += delta;

            // Verifica se a animação de ataque atual terminou
            if (currentAnimation.isAnimationFinished(stateTime)) {
                if (attackStage == 1 && attackTimer > comboWindow) {
                    // Não executou segundo ataque dentro do tempo
                    isAttacking = false;
                    attackStage = 0;
                    resetToIdle();
                } else if (attackStage == 2) {
                    // Segundo ataque finalizado
                    isAttacking = false;
                    attackStage = 0;
                    resetToIdle();
                }
            }

            // Permite iniciar o segundo golpe dentro da janela de combo
            if (attackStage == 1 && Gdx.input.isKeyJustPressed(Input.Keys.X) && attackTimer <= comboWindow) {
                attackStage = 2;
                startAttack(2);
            }

            stateTime += delta;
            return; // Sai — jogador não pode se mover enquanto ataca
        }

        // --- Movimento (WASD) ---
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            direction.y += 1;
            currentAnimation = walkUp;
            facing = Direction.UP;
            moving = true;
        } else if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            direction.y -= 1;
            currentAnimation = walkDown;
            facing = Direction.DOWN;
            moving = true;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            direction.x -= 1;
            currentAnimation = walkLeft;
            facing = Direction.LEFT;
            moving = true;
        } else if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            direction.x += 1;
            currentAnimation = walkRight;
            facing = Direction.RIGHT;
            moving = true;
        }

        // --- Início do ataque ---
        if (Gdx.input.isKeyJustPressed(Input.Keys.X)) {
            attackStage = 1;
            startAttack(1);
            return; // Sai — sem movimento neste frame
        }

        // --- Atualiza posição ---
        // Calcular próxima posição se houver movimento
        if (!direction.isZero()) {
            direction.nor(); // Normaliza para velocidade constante em diagonais
            Vector2 nextPos = new Vector2(pos).add(direction.scl(speed));

            Rectangle nextHitbox = new Rectangle(
                    nextPos.x + TILE_WIDTH / 2.7f,
                    nextPos.y + TILE_HEIGHT / 2f,
                    20f,
                    10f
            );

            boolean collided = false;
            for (Rectangle r : objectHitBoxes) {
                if (nextHitbox.overlaps(r)) {
                    collided = true;
                    break;
                }
            }

            if (!collided) {
                pos.set(nextPos);
            }
        }

        // --- Retorna para idle se parado ---
        if (!moving) {
            resetToIdle();
        }

        stateTime += delta;
    }

    // ============================================================
    // === Renderização ==========================================
    // ============================================================

    /**
     * Renderiza o sprite do jogador na tela.
     * <p>
     * Calcula a posição de desenho para centralizar corretamente o personagem
     * no tile isométrico e aplica uma leve escala no sprite.
     *
     * @param batch objeto usado para desenhar os gráficos na tela
     */
    @Override
    public void render(SpriteBatch batch) {
        float scale = 1.5f;

        // Em ataques, a animação toca uma vez (false); nas demais, em loop (true)
        TextureRegion frame = currentAnimation.getKeyFrame(stateTime, !isAttacking);

        float spriteWidth = frame.getRegionWidth() * scale;
        float spriteHeight = frame.getRegionHeight() * scale;

        float drawX = pos.x + (TILE_WIDTH / 2f) - (spriteWidth / 2f);
        float drawY = pos.y + TILE_HEIGHT - (spriteHeight - 25f);

        batch.draw(frame, drawX, drawY, spriteWidth, spriteHeight);
    }

    // ============================================================
    // === Utilitários privados ==================================
    // ============================================================

    /**
     * Carrega uma animação a partir de uma sequência de imagens em um diretório.
     *
     * @param path          caminho base onde os frames estão armazenados
     * @param frameCount    número total de frames
     * @param frameDuration tempo de exibição de cada frame (em segundos)
     * @return instância de {@link Animation} configurada
     */
    private Animation<TextureRegion> loadAnimation(String path, int frameCount, float frameDuration) {
        TextureRegion[] frames = new TextureRegion[frameCount];
        for (int i = 0; i < frameCount; i++) {
            frames[i] = new TextureRegion(new Texture(Gdx.files.internal(path + "/" + i + ".png")));
        }
        return new Animation<>(frameDuration, frames);
    }

    /**
     * Inicia a animação de ataque conforme a direção atual e estágio do combo.
     *
     * @param stage estágio do ataque (1 = primeiro golpe, 2 = segundo golpe)
     */
    private void startAttack(int stage) {
        isAttacking = true;
        attackTimer = 0f;
        stateTime = 0f;

        switch (stage) {
            case 1 -> {
                switch (facing) {
                    case UP -> currentAnimation = attackUpOne;
                    case DOWN -> currentAnimation = attackDownOne;
                    case LEFT -> currentAnimation = attackLeftOne;
                    case RIGHT -> currentAnimation = attackRightOne;
                }
            }
            case 2 -> {
                switch (facing) {
                    case UP -> currentAnimation = attackUpTwo;
                    case DOWN -> currentAnimation = attackDownTwo;
                    case LEFT -> currentAnimation = attackLeftTwo;
                    case RIGHT -> currentAnimation = attackRightTwo;
                }
            }
        }
    }

    /**
     * Retorna o jogador ao estado idle apropriado com base na última direção.
     */
    private void resetToIdle() {
        switch (facing) {
            case UP -> currentAnimation = idleUp;
            case DOWN -> currentAnimation = idleDown;
            case LEFT -> currentAnimation = idleLeft;
            case RIGHT -> currentAnimation = idleRight;
        }
    }

    // ============================================================
    // === Getters ===============================================
    // ============================================================

    /**
     * Obtém a posição X atual do jogador no espaço isométrico.
     *
     * @return coordenada X
     */
    public float getIsoX() {
        return pos.x;
    }

    /**
     * Obtém a posição Y atual do jogador no espaço isométrico.
     *
     * @return coordenada Y
     */
    public float getIsoY() {
        return pos.y;
    }
}