package com.luna.wallpaper.rbac;

public record AuthenticatedUser(String id, String username, String displayName, String sessionId) {
}
