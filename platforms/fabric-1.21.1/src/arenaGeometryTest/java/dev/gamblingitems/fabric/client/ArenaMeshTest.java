package dev.gamblingitems.fabric.client;

import java.util.ArrayList;
import java.util.List;

/** Verifies the actual vertex emitter, with contours in both orientations. */
public final class ArenaMeshTest {
    private record Vertex(float x, float y, int colour) {}
    private static final int COLOUR = 0xff123456;
    public static void main(String[] args) {
        check(new float[]{0, 0, 100, 0, 100, 40, 0, 40}, new float[]{-1, -1, 101, -1, 101, 41, -1, 41}, 4000);
        check(new float[]{0, 40, 100, 40, 100, 0, 0, 0}, new float[]{-1, 41, 101, 41, 101, -1, -1, -1}, 4000);
        check(new float[]{0, 19, 100, 19, 100, 21, 0, 21}, new float[]{-1, 18, 101, 18, 101, 22, -1, 22}, 200);
        float[] inner = new float[104], outer = new float[104];
        for (int i = 0; i < 52; i++) {
            double angle = i * Math.PI * 2 / 52;
            inner[i * 2] = 50 + (float) Math.cos(angle) * 20;
            inner[i * 2 + 1] = 20 + (float) Math.sin(angle) * 20;
            outer[i * 2] = 50 + (float) Math.cos(angle) * 21;
            outer[i * 2 + 1] = 20 + (float) Math.sin(angle) * 21;
        }
        check(inner, outer, 52 * 200 * Math.sin(2 * Math.PI / 52));
        System.out.println("Arena geometry: winding, fill coverage and alpha fringe passed.");
    }
    private static void check(float[] inner, float[] outer, double expected) {
        List<Vertex> vertices = new ArrayList<>();
        ArenaMesh.polygon(inner, outer, 50, 20, COLOUR, (x, y, c) -> vertices.add(new Vertex(x, y, c)));
        double area = 0;
        for (int i = 0; i < vertices.size(); i += 4) {
            Vertex a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2), d = vertices.get(i + 3);
            double first = cross(a, b, c), second = cross(a, c, d);
            if (first > .001 || second > .001) throw new AssertionError("GUI would cull quad " + i / 4);
            if (i % 8 == 0) {
                if (a.colour != COLOUR || b.colour != COLOUR || c.colour != COLOUR || d.colour != COLOUR)
                    throw new AssertionError("Panel fill lost opacity");
                area -= (first + second) / 2;
            } else if (a.colour != COLOUR || d.colour != COLOUR || b.colour != (COLOUR & 0xffffff) || c.colour != b.colour)
                throw new AssertionError("Invalid alpha fringe");
        }
        if (Math.abs(area - expected) > .01) throw new AssertionError("Incomplete fill: " + area + " vs " + expected);
    }
    private static double cross(Vertex a, Vertex b, Vertex c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }
}
