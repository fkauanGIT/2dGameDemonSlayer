package app.evoMouse;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

/**
 * Gera em código os sprites de rampa e escada (4 direções cada), em pixel art,
 * a partir das texturas de bloco existentes — assim combinam com o resto do mapa.
 * <p>
 * Cada sprite é a "cunha" que fica em cima de um bloco: 32x48 pixels de arte, onde as
 * 32 linhas de baixo coincidem com o bloco e as 16 de cima comportam o nível extra.
 * <p>
 * O desenho é feito por "ray marching" num campo de alturas: para cada pixel, percorre
 * a linha de visão isométrica de frente para trás até encontrar a superfície. Se a
 * encontra por cima é piso (grama/degrau); se bate de lado é uma face vertical (terra/espelho).
 */
public final class SlopeSprites {

    /**
     * Direção para onde a rampa SOBE (em coordenadas de grade)
     */
    public static final int RISE_COL = 1, RISE_ROW = 2, RISE_NEG_COL = 3, RISE_NEG_ROW = 4;

    /**
     * Altura de um nível em pixels de arte (lateral do cubo)
     */
    private static final float LEVEL = 16f;

    /**
     * Degraus por escada
     */
    private static final int STEPS = 4;

    private static final Color STONE_TOP = new Color(0.66f, 0.65f, 0.68f, 1f);
    private static final Color STONE_RISER = new Color(0.45f, 0.44f, 0.50f, 1f);
    private static final Color STONE_SIDE = new Color(0.36f, 0.35f, 0.41f, 1f);
    private static final Color STONE_EDGE = new Color(0.22f, 0.21f, 0.26f, 1f);

    private final Texture[] ramps = new Texture[5];
    private final Texture[] stairs = new Texture[5];

    public SlopeSprites(String grassPath, Pixmap dirt) {
        Pixmap grass = new Pixmap(Gdx.files.internal(grassPath));
        for (int dir = 1; dir <= 4; dir++) {
            ramps[dir] = build(dir, false, grass, dirt);
            stairs[dir] = build(dir, true, grass, dirt);
        }
        grass.dispose();
    }

    public Texture get(int dir, boolean isStairs) {
        return isStairs ? stairs[dir] : ramps[dir];
    }

    /**
     * Fração de subida (0 na base, 1 no topo) num ponto do tile.
     *
     * @param dr deslocamento na linha a partir do centro do tile (-0.5 a 0.5)
     * @param dc deslocamento na coluna a partir do centro do tile (-0.5 a 0.5)
     */
    public static float rise(int dir, float dr, float dc) {
        return switch (dir) {
            case RISE_COL -> dc + 0.5f;
            case RISE_ROW -> dr + 0.5f;
            case RISE_NEG_COL -> 0.5f - dc;
            case RISE_NEG_ROW -> 0.5f - dr;
            default -> 0f;
        };
    }

    /**
     * Altura da superfície desenhada (em pixels de arte). Escadas sobem em degraus.
     */
    private static float surface(int dir, boolean isStairs, float dr, float dc) {
        float t = rise(dir, dr, dc);
        if (!isStairs) return t * LEVEL;
        int step = Math.min(STEPS - 1, (int) (t * STEPS));
        return (step + 1) * LEVEL / STEPS;
    }

    private static Texture build(int dir, boolean isStairs, Pixmap grass, Pixmap dirt) {
        int w = 32, h = 48, offset = 16; // linhas extras acima do bloco
        Pixmap out = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        out.setBlending(Pixmap.Blending.None);
        Color c = new Color();

        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                float rel = py - offset + 0.5f;            // linha no referencial do bloco
                float u = (px + 0.5f - 16f) / 16f;          // dc - dr (fixo na linha de visão)

                boolean wasInside = false;
                for (float s = -1f; s <= 1f; s += 1f / 64f) { // s = dc + dr, da frente para trás
                    float zLine = 8f - 8f * s - rel;
                    if (zLine < 0f) break; // abaixo do topo do bloco: o bloco já desenha isso

                    float dc = (s + u) / 2f, dr = (s - u) / 2f;
                    boolean inside = Math.abs(dc) <= 0.5f && Math.abs(dr) <= 0.5f;
                    if (!inside) {
                        wasInside = false;
                        continue;
                    }

                    float zs = surface(dir, isStairs, dr, dc);
                    if (zLine <= zs) {
                        boolean top = zs - zLine < 0.75f;
                        boolean boundary = !wasInside; // entrou pela borda do tile: face lateral externa
                        if (isStairs) {
                            stairColor(c, top, boundary, dir, dr, dc, px, py);
                        } else if (top) {
                            sampleFlatTop(c, grass, dr, dc);
                        } else {
                            Color.rgba8888ToColor(c, dirt.getPixel(px, (int) (rel + 16f)));
                        }
                        if (c.a > 0) out.drawPixel(px, py, Color.rgba8888(c));
                        break;
                    }
                    wasInside = true;
                }
            }
        }

        Texture texture = new Texture(out);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        out.dispose();
        return texture;
    }

    /**
     * Pega o pixel da grama no ponto equivalente do losango plano do bloco.
     */
    private static void sampleFlatTop(Color c, Pixmap grass, float dr, float dc) {
        int sx = clamp((int) (16f + (dc - dr) * 16f), 0, 31);
        int sy = clamp((int) (8f - (dc + dr) * 8f), 0, 15);
        Color.rgba8888ToColor(c, grass.getPixel(sx, sy));
        if (c.a == 0) Color.rgba8888ToColor(c, grass.getPixel(16, 8));
    }

    private static void stairColor(Color c, boolean top, boolean boundary, int dir,
                                   float dr, float dc, int px, int py) {
        if (top) {
            // Linha escura na quina frontal de cada degrau
            float t = rise(dir, dr, dc) * STEPS;
            boolean edge = t - (float) Math.floor(t) < 0.12f;
            c.set(edge ? STONE_EDGE : STONE_TOP);
        } else {
            c.set(boundary ? STONE_SIDE : STONE_RISER);
        }
        // Leve ruído para textura de pedra
        float noise = (((px * 73856093) ^ (py * 19349663)) & 7) / 7f * 0.06f - 0.03f;
        c.r += noise;
        c.g += noise;
        c.b += noise;
        c.a = 1f;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public void dispose() {
        for (int dir = 1; dir <= 4; dir++) {
            ramps[dir].dispose();
            stairs[dir].dispose();
        }
    }
}
