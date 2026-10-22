package app.evoMouse.seals;

import app.evoMouse.player.Player;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;

/**
 * Buffer de selos do jogador.
 * <p>
 * Funcionamento:
 * <ul>
 *   <li>Segurar <b>ESPAÇO</b> entra na postura de selo (o jogador para de andar)</li>
 *   <li>Cada tecla de selo pressionada é adicionada ao buffer</li>
 *   <li>Ao soltar ESPAÇO, o buffer é comparado com as técnicas registradas</li>
 * </ul>
 * Na postura, as teclas de movimento (WASD) também viram selos, sem conflito com o andar.
 */
public class SealSystem {

    /**
     * Tecla que ativa a postura de selo
     */
    public static final int STANCE_KEY = Input.Keys.SPACE;

    /**
     * Teclas aceitas como selo
     */
    private static final int[] SEAL_KEYS = {
            Input.Keys.Q, Input.Keys.W, Input.Keys.E, Input.Keys.R,
            Input.Keys.A, Input.Keys.S, Input.Keys.D, Input.Keys.F
    };

    /**
     * Quantidade máxima de selos no buffer
     */
    private static final int MAX_LENGTH = 6;

    private final Array<Seal> seals = new Array<>();
    private final StringBuilder buffer = new StringBuilder();
    private boolean inStance = false;

    public void register(Seal seal) {
        seals.add(seal);
    }

    /**
     * Processa a entrada de selos do frame atual.
     *
     * @return true se o jogador está na postura de selo (e não deve se mover)
     */
    public boolean update(Player player) {
        if (Gdx.input.isKeyPressed(STANCE_KEY)) {
            if (!inStance) {
                inStance = true;
                buffer.setLength(0);
            }
            for (int key : SEAL_KEYS) {
                if (Gdx.input.isKeyJustPressed(key) && buffer.length() < MAX_LENGTH) {
                    buffer.append(Input.Keys.toString(key));
                }
            }
            return true;
        }

        if (inStance) {
            inStance = false;
            cast(player);
        }
        return false;
    }

    /**
     * Procura a técnica que corresponde ao buffer e tenta lançá-la.
     */
    private void cast(Player player) {
        String sequence = buffer.toString();
        buffer.setLength(0);
        if (sequence.isEmpty()) return;

        for (Seal seal : seals) {
            if (!seal.sequence().equals(sequence)) continue;

            if (seal.requiresFullKi() && !player.isKiFull()) {
                Gdx.app.log("Selo", seal.name() + " exige o Ki cheio");
                return;
            }
            if (!player.consumeKi(seal.kiCost())) {
                Gdx.app.log("Selo", "Ki insuficiente para " + seal.name());
                return;
            }

            Gdx.app.log("Selo", seal.name() + " lançado!");
            seal.effect().accept(player);
            return;
        }

        Gdx.app.log("Selo", "Sequência inválida: " + sequence);
        player.onSealFailed();
    }

    public boolean isInStance() {
        return inStance;
    }

    /**
     * @return selos digitados até agora na postura atual (para HUD)
     */
    public String getBuffer() {
        return buffer.toString();
    }
}
