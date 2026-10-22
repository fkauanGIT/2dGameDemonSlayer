package app.evoMouse;

/**
 * Consulta de altura do terreno.
 * <p>
 * Toda a lógica do jogo (movimento, colisão, hitbox) acontece no plano do chão.
 * A altura só desloca o desenho para cima: um personagem na altura 2 é desenhado
 * {@code 2 * LEVEL_HEIGHT} pixels acima da sua posição no chão.
 * <p>
 * A altura é contínua: em tiles planos é o nível do tile; em rampas e escadas
 * varia suavemente entre o nível de baixo e o de cima.
 */
public interface Terrain {

    /**
     * Altura de um nível de bloco no mundo (lateral do cubo: 16px de arte em escala 2x)
     */
    float LEVEL_HEIGHT = 32f;

    /**
     * Maior variação de altura (em níveis) permitida num único passo.
     * Rampas mudam pouco a cada passo; a borda de um penhasco muda 1 nível de uma vez.
     */
    float MAX_STEP = 0.3f;

    /**
     * Altura do chão sob os pés de um personagem, em níveis.
     *
     * @param isoX posição X do personagem
     * @param isoY posição Y do personagem (no plano do chão)
     * @return altura (0, 0.5, 1, ...) ou -1 se estiver fora do mapa
     */
    float surfaceAt(float isoX, float isoY);
}
