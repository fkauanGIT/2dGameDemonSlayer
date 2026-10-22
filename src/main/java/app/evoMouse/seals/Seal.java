package app.evoMouse.seals;

import app.evoMouse.player.Player;

import java.util.function.Consumer;

/**
 * Define uma técnica ativada por uma sequência de selos.
 *
 * @param name           nome exibido da técnica
 * @param sequence       sequência de selos (ex: "AAA", "QER") — cada letra é uma tecla
 * @param kiCost         Ki consumido ao lançar
 * @param requiresFullKi se true, só pode ser lançada com o Ki no máximo
 * @param effect         efeito aplicado ao jogador quando a técnica é lançada
 */
public record Seal(String name, String sequence, float kiCost, boolean requiresFullKi,
                   Consumer<Player> effect) {
}
