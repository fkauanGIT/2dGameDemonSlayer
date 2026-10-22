package app.evoMouse.player;

import com.badlogic.gdx.Gdx;
import app.evoMouse.Terrain;
import app.evoMouse.effects.WaterSlash;
import app.evoMouse.seals.Seal;
import app.evoMouse.seals.SealSystem;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
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

    public boolean hasHitThisAttack = false;

    // ============================================================
    // === Ki e selos =============================================
    // ============================================================

    /**
     * Ki máximo do jogador
     */
    private final float maxKi = 100f;

    /**
     * Ki regenerado por segundo (fora da postura de selo)
     */
    private final float kiRegen = 10f;

    /**
     * Ki atual do jogador
     */
    private float ki = maxKi;

    // ============================================================
    // === Vida ===================================================
    // ============================================================

    private final int maxHp = 100;
    private int hp = maxHp;

    /**
     * Tempo restante de invulnerabilidade após levar um golpe
     */
    private float invulnerableTimer = 0f;

    private static final float INVULNERABLE_TIME = 0.8f;

    /**
     * Ponto de reaparecimento ao morrer
     */
    private static final float SPAWN_X = 0f, SPAWN_Y = 0f;

    /**
     * Buffer de selos e técnicas conhecidas
     */
    private final SealSystem sealSystem = new SealSystem();

    /**
     * Cor e tempo restante do flash de feedback (técnica lançada/falhou)
     */
    private final Color flashColor = new Color(Color.WHITE);
    private float flashTimer = 0f;

    /**
     * Altura dos pés do sprite em relação a pos.y (sola fica em pos.y + 39.5).
     * Usada na hitbox, na sombra e na ordem de desenho.
     */
    public static final float FEET_OFFSET_Y = 42f;

    private static final Color STANCE_TINT = new Color(0.7f, 0.8f, 1f, 1f);

    /**
     * Identificador do golpe atual; muda a cada ataque (usado pelos clones)
     */
    private int attackId = 0;

    /**
     * Clones ativos invocados pelo Selo do Reflexo
     */
    private final Array<Clone> clones = new Array<>();

    /**
     * Terreno: limite do mapa e altura do chão; null = chão plano sem limite
     */
    private Terrain terrain;

    /**
     * Altura atual do desenho. Segue o nível do chão suavemente (efeito de degrau)
     */
    private float z = 0f;

    // ============================================================
    // === Pulo e técnica de água =================================
    // ============================================================

    private static final int JUMP_KEY = Input.Keys.Z;
    private static final int WATER_SLASH_KEY = Input.Keys.F;

    /**
     * Velocidade inicial do pulo e gravidade (px/s e px/s²).
     * Altura máxima = v² / 2g ≈ 50px: passa de 1 bloco (32px) com folga, não de 2
     */
    private static final float JUMP_SPEED = 300f;
    private static final float GRAVITY = 900f;

    private boolean airborne = false;
    private float verticalSpeed = 0f;

    private final WaterSlash waterSlash = new WaterSlash();


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

        registerSeals();
    }

    /**
     * Registra as técnicas que o jogador conhece.
     * Os efeitos ainda são só feedback visual; os efeitos reais (clones, projéteis)
     * entram aqui quando forem implementados.
     */
    private void registerSeals() {
        sealSystem.register(new Seal("Selo do Reflexo", "AAA", maxKi, true,
                Player::spawnClones));
        sealSystem.register(new Seal("Bola de Fogo", "QER", 30f, false,
                p -> p.flash(Color.ORANGE))); // TODO: lançar projétil
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
        return boundsAt(getIsoX(), getIsoY());
    }

    /**
     * Hitbox de um personagem com o sprite do jogador na posição informada
     * (usada também pelos clones).
     */
    static Rectangle boundsAt(float x, float y) {
        // Cobre os pés visíveis do sprite: largura dos pés (~18px) e uma faixa
        // fina em volta da sola, centrada em FEET_OFFSET_Y
        float hitboxWidth = 20f;
        float hitboxHeight = 8f;
        float hitboxX = x + TILE_WIDTH / 2f - hitboxWidth / 2f + 1f;
        float hitboxY = y + FEET_OFFSET_Y - hitboxHeight / 2f;

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
     * @param delta          tempo (em segundos) desde o último frame
     * @param objectHitBoxes coleçao de hitboxes de objetos no mapa para verificao de colisão
     */
    @Override
    public void update(float delta, Array<Rectangle> objectHitBoxes) {
        moving = false;
        Vector2 direction = new Vector2();

        if (flashTimer > 0) flashTimer -= delta;
        if (invulnerableTimer > 0) invulnerableTimer -= delta;
        updateZ(delta);
        waterSlash.update(delta);

        // Clones vivem independentemente do estado do jogador
        for (int i = clones.size - 1; i >= 0; i--) {
            Clone clone = clones.get(i);
            clone.update(delta, objectHitBoxes);
            if (clone.isDone()) clones.removeIndex(i);
        }

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

        // --- Postura de selo (segurando ESPAÇO) ---
        if (sealSystem.update(this)) {
            resetToIdle(); // TODO: trocar pela animação de selo quando houver sprite
            stateTime += delta;
            return; // Sai — jogador parado enquanto faz os selos
        }

        ki = Math.min(maxKi, ki + kiRegen * delta);

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

        // --- Pulo (Z) ---
        if (Gdx.input.isKeyJustPressed(JUMP_KEY) && !airborne) {
            airborne = true;
            verticalSpeed = JUMP_SPEED;
        }

        // --- Início do ataque (só no chão) ---
        if (!airborne && Gdx.input.isKeyJustPressed(Input.Keys.X)) {
            attackStage = 1;
            startAttack(1);
            return; // Sai — sem movimento neste frame
        }

        // --- Kata da Água: Corte da Maré (F) ---
        if (!airborne && Gdx.input.isKeyJustPressed(WATER_SLASH_KEY)) {
            if (consumeKi(WaterSlash.KI_COST)) {
                startWaterSlash();
                return;
            }
            onSealFailed(); // Ki insuficiente
        }

        // --- Atualiza posição ---
        // Calcular próxima posição se houver movimento
        if (!direction.isZero()) {
            direction.nor().scl(speed); // Normaliza para velocidade constante em diagonais

            // Tenta o movimento completo; se bloquear, desliza só em X ou só em Y
            if (!tryMove(pos.x + direction.x, pos.y + direction.y, objectHitBoxes)
                    && !tryMove(pos.x + direction.x, pos.y, objectHitBoxes)) {
                tryMove(pos.x, pos.y + direction.y, objectHitBoxes);
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
        // Tom azulado na postura de selo; flash colorido ao lançar/falhar técnica
        Color tint = Color.WHITE;
        if (flashTimer > 0) {
            tint = flashColor;
        } else if (sealSystem.isInStance()) {
            tint = STANCE_TINT;
        }

        renderFrameAt(batch, pos.x, pos.y + z, tint);
        waterSlash.render(batch);
    }

    /**
     * Desenha o frame atual do jogador numa posição qualquer.
     * Usado pelo próprio jogador e pelos clones, que espelham sua animação.
     *
     * @param batch objeto usado para desenhar os gráficos na tela
     * @param x     posição X no mundo isométrico
     * @param y     posição Y no mundo isométrico
     * @param tint  cor aplicada ao sprite (inclui transparência)
     */
    void renderFrameAt(SpriteBatch batch, float x, float y, Color tint) {
        float scale = 1.5f;

        // Em ataques, a animação toca uma vez (false); nas demais, em loop (true)
        TextureRegion frame = currentAnimation.getKeyFrame(stateTime, !isAttacking);

        float spriteWidth = frame.getRegionWidth() * scale;
        float spriteHeight = frame.getRegionHeight() * scale;

        float drawX = x + (TILE_WIDTH / 2f) - (spriteWidth / 2f);
        float drawY = y + TILE_HEIGHT - (spriteHeight - 25f);

        batch.setColor(tint);
        batch.draw(frame, drawX, drawY, spriteWidth, spriteHeight);
        batch.setColor(Color.WHITE);
    }

    // ============================================================
    // === Utilitários privados ==================================
    // ============================================================

    /**
     * Move o jogador para a posição informada se ela estiver dentro do mapa
     * e sem colisão com objetos.
     *
     * @return true se o jogador se moveu
     */
    private boolean tryMove(float nextX, float nextY, Array<Rectangle> objectHitBoxes) {
        // Terreno: não sai do mapa e só passa onde a altura muda pouco (rampas/escadas).
        // Borda de penhasco muda 1 nível de uma vez e bloqueia.
        // Se já está fora (mapa mudou), deixa andar para poder voltar
        // No ar: passa por cima de qualquer chão abaixo dos pés (sobe 1 bloco, desce de platôs);
        // chão mais alto que os pés é parede
        if (terrain != null) {
            float current = terrain.surfaceAt(pos.x, pos.y);
            float next = terrain.surfaceAt(nextX, nextY);
            if (airborne) {
                if (next < 0 || next * Terrain.LEVEL_HEIGHT > z + 4f) return false;
            } else if (current >= 0 && (next < 0 || Math.abs(next - current) > Terrain.MAX_STEP)) {
                return false;
            }
        }

        Rectangle nextHitbox = boundsAt(nextX, nextY);
        for (Rectangle r : objectHitBoxes) {
            if (nextHitbox.overlaps(r)) return false;
        }

        pos.set(nextX, nextY);
        return true;
    }

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
        hasHitThisAttack = false;
        attackId++;
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
    @Override
    public float getIsoY() {
        return pos.y;
    }

    public int getAttackDamage() {
        return switch (attackStage) {
            case 1 -> 25;
            case 2 -> 50; // Combo
            default -> 0;
        };
    }

    public boolean isAttacking() {
        return isAttacking;
    }

    ;

    public boolean hasHitThisAttack() {
        return hasHitThisAttack;
    }

    ;

    public void setHasHitThisAttack(boolean value) {
        this.hasHitThisAttack = value;
    }

    ;

    // ============================================================
    // === Ki e selos =============================================
    // ============================================================

    public float getKi() {
        return ki;
    }

    public float getMaxKi() {
        return maxKi;
    }

    public boolean isKiFull() {
        return ki >= maxKi;
    }

    /**
     * Consome Ki se houver o suficiente.
     *
     * @return true se o Ki foi consumido
     */
    public boolean consumeKi(float amount) {
        if (ki < amount) return false;
        ki -= amount;
        return true;
    }

    public SealSystem getSealSystem() {
        return sealSystem;
    }

    /**
     * Invoca 2 clones ao lado do jogador. Clones anteriores se desfazem.
     */
    public void spawnClones() {
        for (Clone clone : clones) clone.dispel();
        clones.add(new Clone(this, -40f, -10f));
        clones.add(new Clone(this, 40f, -10f));
        flash(Color.WHITE);
    }

    /**
     * Define o terreno (limite do mapa e altura do chão).
     */
    public void setTerrain(Terrain terrain) {
        this.terrain = terrain;
    }

    public Terrain getTerrain() {
        return terrain;
    }

    /**
     * @return altura atual em que o jogador é desenhado
     */
    @Override
    public float getZ() {
        return z;
    }

    /**
     * Altura dos pés. No chão, acompanha o terreno (contínuo em rampas/escadas);
     * no ar, segue a física do pulo até tocar o chão de novo.
     */
    private void updateZ(float delta) {
        if (terrain == null) return;
        float surface = terrain.surfaceAt(pos.x, pos.y);
        if (surface < 0) return;
        float groundZ = surface * Terrain.LEVEL_HEIGHT;

        if (airborne) {
            verticalSpeed -= GRAVITY * delta;
            z += verticalSpeed * delta;
            if (z <= groundZ && verticalSpeed <= 0) {
                z = groundZ;
                airborne = false;
                verticalSpeed = 0f;
            }
        } else if (groundZ < z - 1f) {
            // O chão sumiu sob os pés (ex: mapa regenerado): cai
            airborne = true;
            verticalSpeed = 0f;
        } else {
            z = groundZ;
        }
    }

    /**
     * @return altura do chão sob o jogador (onde a sombra fica, mesmo durante o pulo)
     */
    @Override
    public float getShadowZ() {
        if (terrain == null) return z;
        float surface = terrain.surfaceAt(pos.x, pos.y);
        return surface < 0 ? z : surface * Terrain.LEVEL_HEIGHT;
    }

    public boolean isAirborne() {
        return airborne;
    }

    /**
     * Inicia o Corte da Maré: usa o segundo golpe do combo como animação da espada.
     */
    private void startWaterSlash() {
        attackStage = 2;
        startAttack(2);
        float facingDeg = switch (facing) {
            case RIGHT -> 0f;
            case UP -> 90f;
            case LEFT -> 180f;
            case DOWN -> 270f;
        };
        waterSlash.start(pos.x + TILE_WIDTH / 2f, pos.y + FEET_OFFSET_Y, z, facingDeg);
        Gdx.app.log("Técnica", "Kata da Água: Corte da Maré!");
    }

    public WaterSlash getWaterSlash() {
        return waterSlash;
    }

    /**
     * Pés do jogador no plano do chão
     */
    public float getFeetX() {
        return pos.x + TILE_WIDTH / 2f;
    }

    public float getFeetY() {
        return pos.y + FEET_OFFSET_Y;
    }

    public Array<Clone> getClones() {
        return clones;
    }

    public int getAttackId() {
        return attackId;
    }

    /**
     * Pisca o sprite com a cor informada (feedback de técnica).
     */
    public void flash(Color color) {
        flashColor.set(color);
        flashTimer = 0.3f;
    }

    /**
     * Feedback de sequência de selos inválida.
     */
    /**
     * Recebe dano de um inimigo. Fica invulnerável por um instante; ao zerar a vida,
     * volta ao ponto inicial com a vida cheia.
     *
     * @return true se o dano foi aplicado (false durante a invulnerabilidade)
     */
    public boolean takeDamage(int damage) {
        if (invulnerableTimer > 0) return false;

        hp -= damage;
        invulnerableTimer = INVULNERABLE_TIME;
        flash(Color.RED);

        if (hp <= 0) {
            Gdx.app.log("Player", "Derrotado! Voltando ao refúgio");
            hp = maxHp;
            pos.set(SPAWN_X, SPAWN_Y);
        }
        return true;
    }

    public int getHp() {
        return hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public void onSealFailed() {
        flash(Color.GRAY);
    }
}