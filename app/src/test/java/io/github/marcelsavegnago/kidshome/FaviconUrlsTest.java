package io.github.marcelsavegnago.kidshome;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FaviconUrlsTest {
    @Test
    public void fallsBackToSameOriginFavicon() {
        List<String> urls = FaviconUrls.candidates("https://kids.example/games", null);
        assertEquals(1, urls.size());
        assertEquals("https://kids.example/favicon.ico", urls.get(0));
    }

    @Test
    public void readsIconLinksFromThePage() {
        String html = "<html><head>"
                + "<link rel=\"stylesheet\" href=\"/app.css\">"
                + "<link rel=\"icon\" href=\"/icons/site.png\">"
                + "<link rel='apple-touch-icon' href='touch.png'>"
                + "<link rel=\"icon\" href=\"https://cdn.example/x.png\">"
                + "</head></html>";
        List<String> urls = FaviconUrls.candidates("https://kids.example/games/play", html);
        assertEquals("https://kids.example/icons/site.png", urls.get(0));
        assertEquals("https://kids.example/games/touch.png", urls.get(1));
        assertTrue(urls.contains("https://kids.example/favicon.ico"));
        assertFalse(joined(urls).contains("cdn.example"));
        assertFalse(joined(urls).contains("google.com"));
    }

    @Test
    public void ignoresOtherHostsAndUnsafeSchemes() {
        String html = "<link rel=\"icon\" href=\"https://www.google.com/s2/favicons?domain=kids.example\">"
                + "<link rel=\"icon\" href=\"javascript:alert(1)\">"
                + "<link rel=\"icon\" href=\"data:image/png;base64,aaaa\">"
                + "<link rel=\"icon\" href=\"http://kids.example/plain.ico\">"
                + "<link rel=\"icon\" href=\"/ok.ico\">";
        List<String> urls = FaviconUrls.candidates("https://kids.example/home", html);
        assertEquals(
                java.util.Arrays.asList(
                        "https://kids.example/ok.ico",
                        "https://kids.example/favicon.ico"),
                urls);
    }

    @Test
    public void keepsExplicitPortAndQuery() {
        String html = "<link rel=\"icon\" href=\"/fav.ico?size=64&amp;v=2\">";
        List<String> urls = FaviconUrls.candidates("https://kids.example:8443/a", html);
        assertEquals("https://kids.example:8443/fav.ico?size=64&v=2", urls.get(0));
        assertEquals("https://kids.example:8443/favicon.ico", urls.get(1));
    }

    @Test
    public void treatsDefaultHttpsPortAsTheSameSite() {
        String html = "<link rel=\"icon\" href=\"https://kids.example:443/a.png\">";
        List<String> urls = FaviconUrls.candidates("https://kids.example/home", html);
        assertEquals("https://kids.example/a.png", urls.get(0));
    }

    @Test
    public void rejectsMissingOrNonHttpPages() {
        assertTrue(FaviconUrls.candidates(null, "<link rel=\"icon\" href=\"/a.png\">").isEmpty());
        assertTrue(FaviconUrls.candidates("   ", null).isEmpty());
        assertTrue(FaviconUrls.candidates("file:///tmp/x", null).isEmpty());
        assertTrue(FaviconUrls.candidates("javascript:alert(1)", null).isEmpty());
        assertTrue(FaviconUrls.candidates("https://user:secret@kids.example/", null).isEmpty());
    }

    @Test
    public void capsIconCount() {
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            html.append("<link rel=\"icon\" href=\"/i").append(i).append(".png\">");
        }
        List<String> urls = FaviconUrls.candidates("https://kids.example/", html.toString());
        assertEquals(5, urls.size());
        assertEquals("https://kids.example/favicon.ico", urls.get(urls.size() - 1));
        assertFalse(joined(urls).contains("/i4.png"));
    }

    private static String joined(List<String> urls) {
        StringBuilder sb = new StringBuilder();
        for (String url : urls) {
            sb.append(url).append('\n');
        }
        return sb.toString();
    }
}
