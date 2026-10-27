package vn.homedash.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.Assume;
import org.junit.Test;

/**
 * The parsers on whole pages as webgia.tv served them on 2026-10-24. The pages are not in the public repository (they
 * are webgia.tv's content); save them to src/test/resources to run these, otherwise they are skipped.
 */
public class RealPagesTest {
    private static String page(String name) throws IOException {
        try (InputStream in = RealPagesTest.class.getClassLoader().getResourceAsStream(name)) {
            Assume.assumeNotNull(in);
            assertNotNull(name, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void goldPage() throws IOException {
        Map<String, double[]> g = DashData.parseGoldWeb(page("webgia_gold_pnj.html"));
        assertEquals(142.0, g.get("SJC")[0], 1e-9);
        assertEquals(141.0, g.get("N24K")[0], 1e-9);
        assertEquals(84.79, g.get("61")[0], 1e-9);
        for (double[] v : g.values()) assertTrue("change is a number", !Double.isNaN(v[1]));
    }

    @Test
    public void usdPage() throws IOException {
        double[] r = DashData.parseUsdWeb(page("webgia_vcb.html"));
        assertNotNull(r);
        assertEquals(25610, r[0], 1e-9);
        assertEquals(26020, r[1], 1e-9);
    }

    @Test
    public void fuelPage() throws IOException {
        Map<String, DashData.Fuel> f = DashData.parseFuel(page("webgia_fuel.html"));
        assertEquals(24910, f.get("Xăng E10 RON 95-III").price, 1e-9);
        assertEquals(-1810, f.get("Xăng E10 RON 95-III").change, 1e-9);
        assertEquals(25060, f.get("Xăng E5 RON 92-II").price, 1e-9);
        assertEquals(-1610, f.get("Xăng E5 RON 92-II").change, 1e-9);
        assertEquals(29250, f.get("Dầu diesel 0,05S-II").price, 1e-9);
        assertEquals(450, f.get("Dầu diesel 0,05S-II").change, 1e-9);
    }
}
