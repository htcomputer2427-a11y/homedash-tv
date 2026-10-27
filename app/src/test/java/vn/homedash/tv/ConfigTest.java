package vn.homedash.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class ConfigTest {
    @Test
    public void searchUrlEncodesVietnamese() {
        String url = Config.searchUrl(" Bảo Lộc ");
        assertTrue(url, url.startsWith("https://geocoding-api.open-meteo.com/v1/search?"));
        assertTrue(url, url.endsWith("&name=B%E1%BA%A3o+L%E1%BB%99c"));
        assertTrue(url, url.contains("countryCode=VN"));
    }

    @Test
    public void parsesGeocoderAnswer() {
        String json = "{\"results\":[{\"id\":1,\"name\":\"Bảo Lộc\",\"latitude\":11.55,\"longitude\":107.81,"
                + "\"admin1\":\"Lâm Đồng\"},{\"name\":\"Bảo Lộc\",\"latitude\":9.5,\"longitude\":105.9},"
                + "{\"name\":\"\",\"latitude\":1,\"longitude\":2},{\"name\":\"No coordinates\"}]}";
        List<Config.Place> p = Config.parseSearch(json);
        assertEquals(2, p.size());
        assertEquals("Bảo Lộc", p.get(0).name);
        assertEquals("Lâm Đồng", p.get(0).region);
        assertEquals("Bảo Lộc (Lâm Đồng)", p.get(0).label());
        assertEquals(11.55, p.get(0).lat, 1e-9);
        assertEquals(107.81, p.get(0).lon, 1e-9);
        assertNull(p.get(1).region);
        assertEquals("Bảo Lộc", p.get(1).label());
    }

    @Test
    public void noResultsOrBadJson() {
        assertTrue(Config.parseSearch("{\"generationtime_ms\":0.5}").isEmpty());
        assertTrue(Config.parseSearch("not json").isEmpty());
    }

    @Test
    public void thirtyFourPlacesInsideVietnam() {
        assertEquals(34, Config.PLACES.length);
        Set<String> names = new HashSet<>();
        for (Config.Place p : Config.PLACES) {
            assertTrue(p.name, names.add(p.name));
            assertTrue(p.name, p.lat > 8.5 && p.lat < 23.5);
            assertTrue(p.name, p.lon > 102 && p.lon < 110);
        }
    }
}
