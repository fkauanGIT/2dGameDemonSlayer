package app.evoMouse.enemies;

import app.evoMouse.Terrain;
import app.evoMouse.effects.Impact;
import app.evoMouse.effects.PixelParticles;
import app.evoMouse.factory.EntitysObejcts.WorldObject;
import app.evoMouse.lighting.Light;
import app.evoMouse.objects.TreeObject;
import app.evoMouse.player.Player;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static app.evoMouse.IsometricRenderer.TILE_WIDTH;

/**
 * Oni que se esconde na copa de uma árvore e pula no jogador.
 * <p>
 * Estados:
 * <ol>
 *   <li><b>HIDDEN</b> — na copa; à noite, dois olhos vermelhos piscam entre as folhas</li>
 *   <li><b>JUMPING</b> — o jogador chegou perto: a árvore sacode e ele cai em arco até o chão</li>
 *   <li><b>CHASING</b> — persegue o jogador respeitando rampas, penhascos e troncos</li>
 *   <li><b>ATTACKING</b> — golpe corpo a corpo; o dano sai no meio da animação</li>
 *   <li><b>DYING</b> (do {@link WorldObject}) — animação de queda e depois vira cinzas</li>
 * </ol>
 * A posição (x, y) do Oni é o ponto dos pés no plano do chão.
 */
public class OniEnemy extends WorldObject {

    private enum State {
        HIDDEN, JUMPING, CHASING, ATTACKING, LEAPING, KNOCKBACK
    }

    // === Arremesso (golpe de água) ===

    private static final float KNOCK_GRAVITY = 900f;
    /**
     * Atrito ao deslizar no chão depois de cair (fração da velocidade perdida por segundo)
     */
    private static final float KNOCK_FRICTION = 7f;
    private static final float STUN_TIME = 0.6f;

    private boolean knockbackActive = false;
    private float knockVX, knockVY, knockVZ;
    /**
     * Altura absoluta do corpo durante o arremesso
     */
    private float bodyZ;
    private float stunTimer = 0f;

    /**
     * Salto de penhasco: o Oni desce de qualquer altura e sobe até 1 nível pulando
     */
    private static final float LEAP_TIME = 0.45f;
    private static final float LEAP_DISTANCE = 36f;
    private static final float LEAP_COOLDOWN = 0.5f;

    private float leapFromX, leapFromY, leapFromZ, leapToX, leapToY, leapToZ;
    private float leapCooldown = 0f;

    /**
     * Lado preferido para contornar obstáculos (1 ou -1)
     */
    private float steerSide = 1f;

    private static final float PIXEL_SCALE = 1.5f;

    /**
     * Pés na imagem do sprite (canvas 52x52), a partir do canto inferior esquerdo
     */
    private static final float ANCHOR_X = 26f, ANCHOR_Y = 7f;

    private static final int MAX_HP = 60;
    private static final int ATTACK_DAMAGE = 15;

    /**
     * Distância (no chão) em que o Oni percebe o jogador e pula da árvore
     */
    private static final float AMBUSH_RANGE = 150f;
    private static final float ATTACK_RANGE = 30f;
    private static final float SPEED = 75f; // px/s (o jogador anda ~120)
    private static final float JUMP_TIME = 0.7f;
    private static final float ATTACK_COOLDOWN = 1.1f;
    private static final float HURT_TIME = 0.2f;
    private static final float ASH_TIME = 1.2f;

    private static final Color EYE_COLOR = new Color(1f, 0.1f, 0.1f, 1f);
    private static final Color ASH_START = new Color(0.25f, 0.22f, 0.22f, 1f);
    private static final Color ASH_END = new Color(0.1f, 0.1f, 0.1f, 1f);
    private static final Color EMBER = new Color(1f, 0.35f, 0.1f, 1f);
    private static final Color DUST = new Color(0.55f, 0.48f, 0.38f, 0.8f);

    private final OniSprites sprites;
    private final TreeObject tree;
    private final Terrain terrain;
    private final Array<Rectangle> obstacles;

    /**
     * Objetos do mundo (para achar a árvore atingida durante o arremesso)
     */
    private final Array<WorldObject> world;
    private final PixelParticles particles = new PixelParticles();
    private final Light eyeLight;

    private Player target;
    private State state = State.HIDDEN;
    private OniSprites.Dir facing = OniSprites.Dir.SOUTH;

    private float stateTime = 0f;
    private float animTime = 0f;
    private float attackCooldown = 0f;
    private float hurtTimer = 0f;
    private boolean attackHitDone = false;

    // Pulo: da copa (na tela) até o ponto de pouso (no chão)
    private float jumpStartX, jumpStartY, jumpEndX, jumpEndY;
    private float airZ = 0f; // altura extra durante o pulo

    private float eyeBlinkTimer = MathUtils.random(2f);

    public OniEnemy(TreeObject tree, OniSprites sprites, Terrain terrain,
                    Array<Rectangle> obstacles, Array<WorldObject> world) {
        this.tree = tree;
        this.sprites = sprites;
        this.terrain = terrain;
        this.obstacles = obstacles;
        this.world = world;
        this.hp = MAX_HP;

        // Escondido no tronco da árvore
        this.x = tree.getBaseX();
        this.y = tree.getBaseY();
        this.z = tree.getZ();

        // Enquanto escondido, não pode ser atingido (hitbox de tamanho zero)
        this.hitboxBounds = new Rectangle(x, y, 0f, 0f);
        this.collisionBounds = new Rectangle(x, y, 0f, 0f);

        this.eyeLight = new Light(x, tree.getCanopyScreenY(), 26f, new Color(0.9f, 0.1f, 0.1f, 1f), 0.1f);

        // A morte dura a animação de queda + o tempo virando cinzas
        this.deathDuration = sprites.deathDuration() + ASH_TIME;
    }

    public void setTarget(Player target) {
        this.target = target;
    }

    // ============================================================
    // === Atualização ===========================================
    // ============================================================

    @Override
    public void update(float delta) {
        super.update(delta);
        particles.update(delta);
        animTime += delta;
        if (hurtTimer > 0) hurtTimer -= delta;

        // O arremesso continua mesmo se o golpe matou: ele voa e morre onde cair
        if (knockbackActive) updateKnockback(delta);

        if (!isAlive()) {
            if (deathTimer > sprites.deathDuration()) emitAsh();
            return;
        }
        if (knockbackActive) {
            hitboxBounds.set(x - 18f, y - 10f, 36f, 26f);
            return;
        }

        stateTime += delta;
        if (attackCooldown > 0) attackCooldown -= delta;
        if (target == null) return;

        if (leapCooldown > 0) leapCooldown -= delta;

        switch (state) {
            case HIDDEN -> updateHidden(delta);
            case JUMPING -> updateJumping();
            case CHASING -> updateChasing(delta);
            case ATTACKING -> updateAttacking();
            case LEAPING -> updateLeaping();
            case KNOCKBACK -> {
            }
        }

        hitboxBounds.set(x - 18f, y - 10f, 36f, 26f);
    }

    private void updateHidden(float delta) {
        eyeBlinkTimer -= delta;
        if (eyeBlinkTimer < -0.15f) eyeBlinkTimer = MathUtils.random(1.5f, 3.5f);

        // Pula quando o jogador chega perto — ou quando derrubam a árvore dele
        if (distanceToTarget() < AMBUSH_RANGE || !tree.isAlive()) startJump();
    }

    private void startJump() {
        state = State.JUMPING;
        stateTime = 0f;
        animTime = 0f;
        tree.rustle();

        jumpStartX = tree.getBaseX();
        jumpStartY = tree.getCanopyScreenY();

        // Pousa entre a árvore e o jogador; se ali não der (fora do mapa, outra altura ou
        // em cima de um tronco), tenta outros pontos ao redor da árvore
        float dx = targetX() - tree.getBaseX(), dy = targetY() - tree.getBaseY();
        float len = Math.max(1f, (float) Math.hypot(dx, dy));
        float treeLevel = tree.getZ() / Terrain.LEVEL_HEIGHT;
        float baseAngle = MathUtils.atan2(dy / len, dx / len);

        // Último recurso: o pé do tronco (sempre dentro do mapa; tryMove deixa sair do tronco)
        jumpEndX = tree.getBaseX();
        jumpEndY = tree.getBaseY();
        search:
        for (float radius : new float[]{45f, 30f, 60f}) {
            for (int i = 0; i < 16; i++) {
                // 0, +22.5, -22.5, +45, -45, ... a partir da direção do jogador
                float angle = baseAngle + ((i + 1) / 2) * MathUtils.PI / 8f * (i % 2 == 0 ? 1 : -1);
                float ex = tree.getBaseX() + MathUtils.cos(angle) * radius;
                float ey = tree.getBaseY() + MathUtils.sin(angle) * radius;
                float level = surfaceAt(ex, ey);
                if (level >= 0 && Math.abs(level - treeLevel) <= Terrain.MAX_STEP && !blocked(ex, ey)) {
                    jumpEndX = ex;
                    jumpEndY = ey;
                    break search;
                }
            }
        }
        facing = OniSprites.Dir.fromVector(dx, dy);
    }

    private void updateJumping() {
        float t = Math.min(1f, stateTime / JUMP_TIME);
        float groundZ = surfaceAt(jumpEndX, jumpEndY) * Terrain.LEVEL_HEIGHT;

        // Posição no chão vai do tronco até o pouso; a altura vem da copa até o chão em arco
        x = MathUtils.lerp(jumpStartX, jumpEndX, t);
        y = MathUtils.lerp(tree.getBaseY(), jumpEndY, t);
        z = groundZ;
        float startHeight = jumpStartY - (tree.getBaseY() + groundZ);
        airZ = startHeight * (1f - t) + 40f * 4f * t * (1f - t);

        if (t >= 1f) {
            airZ = 0f;
            landingDust();
            changeState(State.CHASING);
        }
    }

    // ============================================================
    // === Arremesso =============================================
    // ============================================================

    /**
     * Arremessa o Oni para longe (golpe de água). Ignorado enquanto escondido ou pulando da árvore.
     *
     * @param dirX direção do arremesso no chão (normalizada)
     * @param dirY direção do arremesso no chão (normalizada)
     */
    public void knockback(float dirX, float dirY, float speed, float lift) {
        if (state == State.HIDDEN || state == State.JUMPING) return;

        knockVX = dirX * speed;
        knockVY = dirY * speed;
        knockVZ = lift;
        bodyZ = z + airZ;
        knockbackActive = true;
        if (isAlive()) changeState(State.KNOCKBACK);
    }

    /**
     * Voo do arremesso, com colisão:
     * <ul>
     *   <li>árvore no caminho: a árvore treme e o Oni para ali e cai</li>
     *   <li>morro mais alto que o corpo: bate, para e cai</li>
     *   <li>beirada de platô: continua voando e cai no nível de baixo</li>
     *   <li>borda do mapa: para</li>
     * </ul>
     */
    private void updateKnockback(float delta) {
        float nx = x + knockVX * delta, ny = y + knockVY * delta;
        float next = surfaceAt(nx, ny);

        if (next < 0f) {
            stopHorizontal();
        } else if (next * Terrain.LEVEL_HEIGHT > bodyZ + 4f) {
            // Parede de morro: bate e escorrega para baixo
            stopHorizontal();
            impactDust();
        } else {
            TreeObject hitTree = treeAt(nx, ny);
            if (hitTree != null) {
                hitTree.rustle();
                stopHorizontal();
                impactDust();
            } else {
                x = nx;
                y = ny;
            }
        }

        // Vertical: gravidade até tocar o chão (que pode ter ficado mais baixo)
        float groundZ = Math.max(0f, surfaceAt(x, y)) * Terrain.LEVEL_HEIGHT;
        knockVZ -= KNOCK_GRAVITY * delta;
        bodyZ += knockVZ * delta;

        boolean onGround = bodyZ <= groundZ;
        if (onGround) {
            if (knockVZ < -150f) landingDust();
            bodyZ = groundZ;
            knockVZ = 0f;
            // Desliza no chão perdendo velocidade
            float keep = Math.max(0f, 1f - KNOCK_FRICTION * delta);
            knockVX *= keep;
            knockVY *= keep;
        }
        z = groundZ;
        airZ = bodyZ - groundZ;

        if (onGround && Math.hypot(knockVX, knockVY) < 20f) {
            knockbackActive = false;
            airZ = 0f;
            if (isAlive()) {
                stunTimer = STUN_TIME;
                changeState(State.CHASING);
            }
        }
    }

    private void stopHorizontal() {
        knockVX = 0f;
        knockVY = 0f;
    }

    /**
     * @return árvore viva cujo tronco os pés do Oni tocariam nesse ponto, ou null
     */
    private TreeObject treeAt(float footX, float footY) {
        Rectangle feet = new Rectangle(footX - 9f, footY - 4f, 18f, 8f);
        // Laço por índice: esta lista já está sendo percorrida pelo renderer neste momento
        for (int i = 0; i < world.size; i++) {
            if (world.get(i) instanceof TreeObject tree && tree.isAlive() && feet.overlaps(tree.getBoundsCollision())) {
                return tree;
            }
        }
        return null;
    }

    private void impactDust() {
        Impact.shake(0.4f);
        for (int i = 0; i < 10; i++) {
            PixelParticles.Particle p = particles.emit(x + MathUtils.random(-8f, 8f), y + z + airZ + MathUtils.random(5f, 30f),
                    -knockVX * 0.1f + MathUtils.random(-40f, 40f), MathUtils.random(10f, 50f),
                    MathUtils.random(0.3f, 0.6f), MathUtils.randomBoolean() ? 2f : 1f, DUST, DUST);
            PixelParticles.setGravity(p, 120f);
        }
    }

    private void updateChasing(float delta) {
        // Atordoado depois de ser arremessado
        if (stunTimer > 0f) {
            stunTimer -= delta;
            return;
        }

        float dx = targetX() - x, dy = targetY() - y;
        float dist = (float) Math.hypot(dx, dy);
        facing = OniSprites.Dir.fromVector(dx, dy);

        // Só ataca na mesma altura; com o jogador em cima/embaixo de um penhasco, salta até ele
        boolean sameHeight = Math.abs(surfaceAt(x, y) - surfaceAt(targetX(), targetY())) < 0.5f;
        if (sameHeight && dist < ATTACK_RANGE && attackCooldown <= 0) {
            changeState(State.ATTACKING);
            attackHitDone = false;
            return;
        }
        if (sameHeight && dist < ATTACK_RANGE * 0.8f) return; // perto o bastante: espera o golpe recarregar

        float step = SPEED * delta;
        float dirX = dx / dist, dirY = dy / dist;

        // Tenta direto. Se bloquear:
        // - com o jogador em outra altura, o bloqueio é o penhasco: salta por cima dele;
        // - senão (ou se não houver onde pousar), desvia em ângulos crescentes (contorna troncos)
        if (!tryMove(x + dirX * step, y + dirY * step)) {
            if (!sameHeight && leapCooldown <= 0) {
                tryLeap(dirX, dirY);
                if (state == State.LEAPING) return;
            }
            if (!steer(dirX, dirY, step) && leapCooldown <= 0) tryLeap(dirX, dirY);
        }
        z = Math.max(0f, surfaceAt(x, y)) * Terrain.LEVEL_HEIGHT;
    }

    /**
     * Anda na direção do alvo ou, se bloqueado, na direção girada mais próxima que estiver livre.
     * Lembra o lado do último desvio para não ficar indeciso entre esquerda e direita.
     */
    private boolean steer(float dirX, float dirY, float step) {
        float[] angles = {0f, 45f, 90f, 135f};
        for (float angle : angles) {
            for (int side = 0; side < (angle == 0f ? 1 : 2); side++) {
                float a = angle * MathUtils.degreesToRadians * (side == 0 ? steerSide : -steerSide);
                float cos = MathUtils.cos(a), sin = MathUtils.sin(a);
                float mx = (dirX * cos - dirY * sin) * step;
                float my = (dirX * sin + dirY * cos) * step;
                if (tryMove(x + mx, y + my)) {
                    if (angle != 0f && side == 1) steerSide = -steerSide;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Salta para um ponto à frente se for mais baixo (qualquer altura) ou até 1 nível mais alto.
     */
    private void tryLeap(float dirX, float dirY) {
        float from = surfaceAt(x, y);
        float[] distances = {LEAP_DISTANCE, LEAP_DISTANCE * 0.7f, LEAP_DISTANCE * 1.35f};
        float[] angles = {0f, 30f, -30f};

        // Procura um ponto de pouso livre: à frente primeiro, depois mais perto/longe e de lado
        for (float angle : angles) {
            float a = angle * MathUtils.degreesToRadians;
            float ax = dirX * MathUtils.cos(a) - dirY * MathUtils.sin(a);
            float ay = dirX * MathUtils.sin(a) + dirY * MathUtils.cos(a);
            for (float distance : distances) {
                float tx = x + ax * distance, ty = y + ay * distance;
                float to = surfaceAt(tx, ty);
                // Só é chamado quando andar não deu: qualquer ponto até 1 nível acima serve
                if (to < 0 || to - from > 1.05f) continue;
                if (blocked(tx, ty)) continue;

                leapFromX = x;
                leapFromY = y;
                leapFromZ = z;
                leapToX = tx;
                leapToY = ty;
                leapToZ = to * Terrain.LEVEL_HEIGHT;
                changeState(State.LEAPING);
                return;
            }
        }
    }

    /**
     * @return true se os pés do Oni nesse ponto encostariam num tronco/obstáculo
     */
    private boolean blocked(float footX, float footY) {
        Rectangle feet = new Rectangle(footX - 9f, footY - 4f, 18f, 8f);
        for (Rectangle r : obstacles) {
            if (feet.overlaps(r)) return true;
        }
        return false;
    }

    private void updateLeaping() {
        float t = Math.min(1f, stateTime / LEAP_TIME);
        x = MathUtils.lerp(leapFromX, leapToX, t);
        y = MathUtils.lerp(leapFromY, leapToY, t);

        // A sombra (z) vai direto para o chão de destino; o corpo faz o arco por cima
        z = leapToZ;
        float bodyZ = MathUtils.lerp(leapFromZ, leapToZ, t) + 30f * 4f * t * (1f - t);
        airZ = bodyZ - leapToZ;

        if (t >= 1f) {
            airZ = 0f;
            leapCooldown = LEAP_COOLDOWN;
            landingDust();
            changeState(State.CHASING);
        }
    }

    private void updateAttacking() {
        float duration = sprites.attackDuration();

        // O dano sai na metade do golpe, se o jogador ainda estiver perto
        if (!attackHitDone && stateTime >= duration * 0.5f) {
            attackHitDone = true;
            if (distanceToTarget() < ATTACK_RANGE * 1.4f && target.takeDamage(ATTACK_DAMAGE)) Impact.shake(0.3f);
        }
        if (stateTime >= duration) {
            attackCooldown = ATTACK_COOLDOWN;
            changeState(State.CHASING);
        }
    }

    private boolean tryMove(float nx, float ny) {
        float current = surfaceAt(x, y), next = surfaceAt(nx, ny);
        // Fora do mapa (não deveria acontecer), deixa voltar para qualquer chão válido
        if (next < 0 || (current >= 0 && Math.abs(next - current) > Terrain.MAX_STEP)) return false;
        // Se já está encostado num tronco (pousou nele), deixa se afastar
        if (blocked(nx, ny) && !blocked(x, y)) return false;

        x = nx;
        y = ny;
        return true;
    }

    private void changeState(State next) {
        state = next;
        stateTime = 0f;
        animTime = 0f;
    }

    @Override
    public void takeDamage(int damage) {
        if (state == State.HIDDEN || state == State.JUMPING || state == State.LEAPING) return;
        super.takeDamage(damage);
        hurtTimer = HURT_TIME;
        if (!isAlive()) {
            animTime = 0f;
            hitboxBounds.set(x, y, 0f, 0f);
        }
    }

    // ============================================================
    // === Renderização ==========================================
    // ============================================================

    @Override
    protected void renderAlive(SpriteBatch batch) {
        if (state == State.HIDDEN) {
            renderEyes(batch);
            return;
        }

        TextureRegion frame = switch (state) {
            case JUMPING -> sprites.jump(stateTime / JUMP_TIME, true);
            case LEAPING -> sprites.jump(stateTime / LEAP_TIME, false);
            case KNOCKBACK -> sprites.jump(airZ > 2f ? 0.5f : 0.85f, false); // no ar / caído
            case ATTACKING -> sprites.attack(facing, animTime);
            default -> sprites.walk(facing, animTime);
        };

        if (hurtTimer > 0) batch.setColor(1f, 0.45f, 0.45f, batch.getColor().a);
        drawFrame(batch, frame);
        batch.setColor(1f, 1f, 1f, batch.getColor().a);
        particles.render(batch);
    }

    /**
     * Morte: animação de queda; depois o corpo some enquanto vira cinzas.
     */
    @Override
    protected void renderDying(SpriteBatch batch) {
        float fallTime = sprites.deathDuration();
        Color c = batch.getColor();
        float r = c.r, g = c.g, b = c.b, a = c.a;

        if (deathTimer > fallTime) {
            float fade = 1f - (deathTimer - fallTime) / ASH_TIME;
            batch.setColor(r * 0.5f, g * 0.45f, b * 0.45f, a * Math.max(0f, fade));
        }
        drawFrame(batch, sprites.death(Math.min(deathTimer, fallTime - 0.001f)));
        batch.setColor(r, g, b, a);
        particles.render(batch);
    }

    private void drawFrame(SpriteBatch batch, TextureRegion frame) {
        float w = frame.getRegionWidth() * PIXEL_SCALE;
        float h = frame.getRegionHeight() * PIXEL_SCALE;
        batch.draw(frame, x - ANCHOR_X * PIXEL_SCALE, y + z + airZ - ANCHOR_Y * PIXEL_SCALE, w, h);
    }

    /**
     * Dois pixels vermelhos entre as folhas. Só se destacam à noite (a luz os acompanha).
     */
    private void renderEyes(SpriteBatch batch) {
        if (eyeBlinkTimer < 0f) return; // piscando
        float ex = tree.getBaseX() - 6f, ey = tree.getCanopyScreenY() - 10f;
        Color old = batch.getColor().cpy();
        batch.setColor(EYE_COLOR);
        batch.draw(sprites.pixel(), ex, ey, 3f, 2f);
        batch.draw(sprites.pixel(), ex + 9f, ey, 3f, 2f);
        batch.setColor(old);
    }

    // ============================================================
    // === Partículas ============================================
    // ============================================================

    private void landingDust() {
        for (int i = 0; i < 14; i++) {
            float angle = MathUtils.random(MathUtils.PI2);
            PixelParticles.Particle p = particles.emit(x + MathUtils.cos(angle) * 8f, y + z + MathUtils.sin(angle) * 3f,
                    MathUtils.cos(angle) * MathUtils.random(30f, 60f), MathUtils.random(5f, 25f),
                    MathUtils.random(0.3f, 0.6f), MathUtils.randomBoolean() ? 2f : 1f, DUST, DUST);
            PixelParticles.setGravity(p, 60f);
        }
    }

    /**
     * Cinzas e brasas subindo do corpo, como um Oni morto pela lâmina.
     */
    private void emitAsh() {
        for (int i = 0; i < 3; i++) {
            boolean ember = MathUtils.randomBoolean(0.25f);
            PixelParticles.Particle p = particles.emit(x + MathUtils.random(-18f, 18f), y + z + MathUtils.random(0f, 20f),
                    MathUtils.random(-6f, 6f), MathUtils.random(20f, 45f), MathUtils.random(0.8f, 1.4f),
                    ember ? 1f : 2f, ember ? EMBER : ASH_START, ember ? EMBER : ASH_END);
            PixelParticles.setSway(p, 10f, 2f);
        }
    }

    // ============================================================
    // === Auxiliares ============================================
    // ============================================================

    /**
     * Pés do jogador no plano do chão
     */
    private float targetX() {
        return target.getIsoX() + TILE_WIDTH / 2f;
    }

    private float targetY() {
        return target.getIsoY() + Player.FEET_OFFSET_Y;
    }

    private float distanceToTarget() {
        return (float) Math.hypot(targetX() - x, targetY() - y);
    }

    /**
     * Altura do chão (em níveis) num ponto dos pés
     */
    private float surfaceAt(float footX, float footY) {
        return terrain.surfaceAt(footX - TILE_WIDTH / 2f, footY - Player.FEET_OFFSET_Y);
    }

    @Override
    public float getShadowWidth() {
        // Sem sombra escondido; durante o pulo a sombra mostra onde ele vai cair
        return state == State.HIDDEN ? 0f : 24f;
    }

    @Override
    public float getBaseX() {
        return x;
    }

    @Override
    public float getBaseY() {
        return y;
    }

    /**
     * @return olhos brilhando na copa enquanto escondido
     */
    @Override
    public Light getLight() {
        if (state != State.HIDDEN || eyeBlinkTimer < 0f) return null;
        eyeLight.x = tree.getBaseX();
        eyeLight.y = tree.getCanopyScreenY() - 9f;
        return eyeLight;
    }

    /**
     * @return Y do meio do corpo na tela (com altura do terreno e do pulo)
     */
    public float getBodyScreenY() {
        return y + z + airZ + 28f;
    }

    public boolean isHidden() {
        return state == State.HIDDEN;
    }
}
