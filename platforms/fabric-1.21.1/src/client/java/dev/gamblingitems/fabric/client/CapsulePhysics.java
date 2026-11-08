package dev.gamblingitems.fabric.client;

import dev.gamblingitems.fabric.block.GameStationEntity;

/**
 * The capsules heaped in a gacha globe, as little balls under gravity: they fall, roll over one another,
 * bounce on the glass, and are stirred up while the crank turns. Purely for show, on each client.
 *
 * <p>Kept cheap: a fixed step per game tick (twice over, so the heap stays stable), at most 26 balls so
 * every pair is tested directly, and once the heap has settled it sleeps and costs nothing until stirred.
 * The renderer interpolates between the last two steps, so the motion stays smooth at any frame rate.
 */
final class CapsulePhysics {
    static final int COUNT = 26;
    static final float RADIUS = 0.75f;
    private static final float GRAVITY = 60, BOUNCE = 0.35f, FRICTION = 0.96f, STEP = 1 / 40f;
    /** Steps a heap may catch up on in one frame, after it was out of sight. */
    private static final int MAX_CATCH_UP = 40;
    private final float cu, cy, cz, inner;
    private final float[] x = new float[COUNT], y = new float[COUNT], z = new float[COUNT];
    private final float[] px = new float[COUNT], py = new float[COUNT], pz = new float[COUNT];
    private final float[] vx = new float[COUNT], vy = new float[COUNT], vz = new float[COUNT];
    private final float[] spin = new float[COUNT], previousSpin = new float[COUNT];
    private long tick = Long.MIN_VALUE;
    private int calm;

    private CapsulePhysics(float cu, float cy, float cz, float globe) {
        this.cu = cu;
        this.cy = cy;
        this.cz = cz;
        this.inner = globe - RADIUS - 0.25f;
        // Laid out as the old still heap, then left to fall into place.
        for (int index = 0; index < COUNT; index++) {
            int layer = index / 9;
            double angle = index * 2.399, ring = Math.sqrt((index % 9 + 0.5) / 9.0) * (inner - 1);
            x[index] = cu + (float) (Math.cos(angle) * ring);
            z[index] = cz + (float) (Math.sin(angle) * ring);
            y[index] = cy - inner + 0.5f + layer * 1.6f;
        }
        save();
    }

    static CapsulePhysics of(GameStationEntity station, float cu, float cy, float cz, float globe) {
        if (station.clientState instanceof CapsulePhysics physics) return physics;
        CapsulePhysics physics = new CapsulePhysics(cu, cy, cz, globe);
        station.clientState = physics;
        return physics;
    }

    /** Brings the heap to this game tick; stir, from 0 to 1, is how hard the crank churns it. */
    void advance(long now, float stir) {
        if (tick == Long.MIN_VALUE) tick = now - MAX_CATCH_UP;
        int steps = (int) Math.min(MAX_CATCH_UP, Math.max(0, now - tick));
        tick = now;
        for (int step = 0; step < steps; step++) {
            if (stir <= 0 && calm > 20) {
                save();
                continue;
            }
            save();
            for (int sub = 0; sub < 2; sub++) step(stir);
        }
    }

    private void save() {
        System.arraycopy(x, 0, px, 0, COUNT);
        System.arraycopy(y, 0, py, 0, COUNT);
        System.arraycopy(z, 0, pz, 0, COUNT);
        System.arraycopy(spin, 0, previousSpin, 0, COUNT);
    }

    private void step(float stir) {
        float fastest = 0;
        for (int i = 0; i < COUNT; i++) {
            vy[i] -= GRAVITY * STEP;
            if (stir > 0) {
                // The paddle under the heap swirls it round and lifts what lies low.
                float dx = x[i] - cu, dz = z[i] - cz;
                float low = Math.max(0, (cy - y[i]) / inner);
                vx[i] += -dz * 2.2f * stir * STEP * 10;
                vz[i] += dx * 2.2f * stir * STEP * 10;
                vy[i] += (GRAVITY * 1.25f * low + 18 * (float) Math.random()) * stir * STEP;
            }
            x[i] += vx[i] * STEP;
            y[i] += vy[i] * STEP;
            z[i] += vz[i] * STEP;
        }
        float minimum = RADIUS * 2;
        for (int i = 0; i < COUNT; i++) {
            for (int j = i + 1; j < COUNT; j++) {
                float dx = x[j] - x[i], dy = y[j] - y[i], dz = z[j] - z[i];
                float squared = dx * dx + dy * dy + dz * dz;
                if (squared >= minimum * minimum || squared < 1e-6f) continue;
                float distance = (float) Math.sqrt(squared), push = (minimum - distance) / 2 / distance;
                x[i] -= dx * push; y[i] -= dy * push; z[i] -= dz * push;
                x[j] += dx * push; y[j] += dy * push; z[j] += dz * push;
                // Only the speed towards each other is taken away, so the balls roll off one another.
                float nx = dx / distance, ny = dy / distance, nz = dz / distance;
                float closing = (vx[j] - vx[i]) * nx + (vy[j] - vy[i]) * ny + (vz[j] - vz[i]) * nz;
                if (closing < 0) {
                    float impulse = -closing * (1 + BOUNCE) / 2;
                    vx[i] -= nx * impulse; vy[i] -= ny * impulse; vz[i] -= nz * impulse;
                    vx[j] += nx * impulse; vy[j] += ny * impulse; vz[j] += nz * impulse;
                }
            }
        }
        for (int i = 0; i < COUNT; i++) {
            float dx = x[i] - cu, dy = y[i] - cy, dz = z[i] - cz;
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > inner) {
                float nx = dx / distance, ny = dy / distance, nz = dz / distance;
                x[i] = cu + nx * inner;
                y[i] = cy + ny * inner;
                z[i] = cz + nz * inner;
                float outward = vx[i] * nx + vy[i] * ny + vz[i] * nz;
                if (outward > 0) {
                    vx[i] = (vx[i] - nx * outward * (1 + BOUNCE)) * FRICTION;
                    vy[i] = (vy[i] - ny * outward * (1 + BOUNCE)) * FRICTION;
                    vz[i] = (vz[i] - nz * outward * (1 + BOUNCE)) * FRICTION;
                }
            }
            float speed = vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i];
            spin[i] += (float) Math.sqrt(speed) * STEP / RADIUS;
            fastest = Math.max(fastest, speed);
        }
        calm = fastest < 0.6f && stir <= 0 ? calm + 1 : 0;
        if (calm > 20) {
            // Settled: the heap rests exactly where it lies until the crank turns again.
            java.util.Arrays.fill(vx, 0);
            java.util.Arrays.fill(vy, 0);
            java.util.Arrays.fill(vz, 0);
        }
    }

    float x(int index, float partial) { return px[index] + (x[index] - px[index]) * partial; }
    float y(int index, float partial) { return py[index] + (y[index] - py[index]) * partial; }
    float z(int index, float partial) { return pz[index] + (z[index] - pz[index]) * partial; }
    float spin(int index, float partial) { return previousSpin[index] + (spin[index] - previousSpin[index]) * partial; }
}
