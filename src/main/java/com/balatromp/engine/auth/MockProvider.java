package com.balatromp.engine.auth;

/**
 * A dev/test login provider that needs no external service or secrets: the
 * authorization "code" IS the chosen identity. Always registered so local
 * development and the test suite can exercise the full account/token flow
 * without real OAuth credentials. (Never use for ranked.)
 */
public final class MockProvider implements OAuthProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        return redirectUri + "?provider=mock&state=" + state + "&code=<your-name>";
    }

    @Override
    public Identity exchange(String code, String redirectUri) {
        String user = (code == null || code.isBlank()) ? "guest" : code.trim();
        return new Identity(user, user);
    }
}
