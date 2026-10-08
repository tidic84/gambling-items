package dev.gamblingitems.fabric.client;

/** Emits front-facing GUI quads independently of Minecraft for headless regression checks. */
final class ArenaMesh {
    @FunctionalInterface interface Sink { void vertex(float x, float y, int colour); }
    private ArenaMesh() {}

    static void polygon(float[] inner, float[] outer, float cx, float cy, int colour, Sink sink) {
        float area = 0;
        for (int i = 0; i < inner.length; i += 2) {
            int next = (i + 2) % inner.length;
            area += inner[i] * inner[next + 1] - inner[next] * inner[i + 1];
        }
        int transparent = colour & 0x00ffffff;
        for (int i = 0; i < inner.length; i += 2) {
            int next = (i + 2) % inner.length;
            int a = area > 0 ? next : i, b = area > 0 ? i : next;
            // GuiGraphics.fill uses negative screen-space winding. GUI render types cull the other side.
            sink.vertex(cx, cy, colour);
            sink.vertex(inner[a], inner[a + 1], colour);
            sink.vertex(inner[b], inner[b + 1], colour);
            sink.vertex(inner[b], inner[b + 1], colour);
            sink.vertex(inner[a], inner[a + 1], colour);
            sink.vertex(outer[a], outer[a + 1], transparent);
            sink.vertex(outer[b], outer[b + 1], transparent);
            sink.vertex(inner[b], inner[b + 1], colour);
        }
    }
}
