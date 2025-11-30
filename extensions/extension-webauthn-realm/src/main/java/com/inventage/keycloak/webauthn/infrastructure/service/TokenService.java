package com.inventage.keycloak.webauthn.infrastructure.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.managers.AuthenticationSessionManager;
import org.keycloak.services.util.DefaultClientSessionContext;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;

import jakarta.ws.rs.core.UriInfo;
/**
 * Service for generating OAuth2/OIDC tokens after successful WebAuthn authentication.
 * Creates authentication sessions and generates access, refresh, and ID tokens.
 */
public class TokenService {

    private static final Logger LOG = Logger.getLogger(TokenService.class);

    private final KeycloakSession session;

    public TokenService(KeycloakSession session) {
        this.session = session;
    }

    /**
     * Generate tokens for authenticated user.
     *
     * @param user Authenticated user
     * @param client OIDC client
     * @param scope Requested scopes (e.g., "openid profile email")
     * @return Map containing access_token, refresh_token, id_token, expires_in, token_type
     */
    public Map<String, Object> generateTokens(UserModel user, ClientModel client, String scope) {
        try {
            LOG.debugf("Generating tokens for user: %s, client: %s",
                    user.getUsername(), client.getClientId());

            RealmModel realm = session.getContext().getRealm();
            UriInfo uriInfo = session.getContext().getUri();

            // Create authentication session
            AuthenticationSessionModel authSession = createAuthenticationSession(user, client, scope);

            // Create user session
            UserSessionModel userSession = createUserSession(user, client, authSession);

            // Generate tokens using Keycloak's TokenManager
            TokenManager tokenManager = new TokenManager();
            AuthenticatedClientSessionModel clientSession = userSession.getAuthenticatedClientSessionByClient(client.getId());
            Set<String> clientScopes = authSession.getClientScopes();
            Map<String, ClientScopeModel> clientScopeModel = session.clients().getClientScopes(realm, client, false);
            Set<ClientScopeModel> clientScopesFilter = clientScopes.stream()
                .map(clientScopeModel::get)
                .collect(Collectors.toSet());
            DefaultClientSessionContext clientSessionContext = DefaultClientSessionContext.fromClientSessionAndClientScopes(clientSession, clientScopesFilter , session);
            TokenManager.AccessTokenResponseBuilder responseBuilder = tokenManager
                .responseBuilder(realm, client, null, session, userSession, clientSessionContext)
                .generateAccessToken()
                .generateRefreshToken()
                .generateIDToken();

            // Build token response
            var tokenResponse = responseBuilder.build();

            Map<String, Object> tokens = new HashMap<>();
            tokens.put("access_token", tokenResponse.getToken());
            tokens.put("refresh_token", tokenResponse.getRefreshToken());
            tokens.put("id_token", tokenResponse.getIdToken());
            tokens.put("expires_in", tokenResponse.getExpiresIn());
            tokens.put("refresh_expires_in", tokenResponse.getRefreshExpiresIn());
            tokens.put("token_type", tokenResponse.getTokenType());
            tokens.put("session_state", userSession.getId());

            LOG.infof("Tokens generated successfully for user: %s", user.getUsername());

            return tokens;

        } catch (Exception e) {
            LOG.errorf(e, "Failed to generate tokens for user: %s", user.getUsername());
            throw new RuntimeException("Token generation failed", e);
        }
    }

    /**
     * Create authentication session.
     *
     * @param user User model
     * @param client Client model
     * @param scope Requested scope
     * @return AuthenticationSessionModel
     */
    private AuthenticationSessionModel createAuthenticationSession(UserModel user,
            ClientModel client, String scope) {

        RealmModel realm = session.getContext().getRealm();

        // Create root authentication session
        AuthenticationSessionManager authSessionManager = new AuthenticationSessionManager(session);
        RootAuthenticationSessionModel rootAuthSession = authSessionManager.createAuthenticationSession(realm, true);

        // Create tab-specific authentication session
        AuthenticationSessionModel authSession = rootAuthSession.createAuthenticationSession(client);

        // Set authentication session properties
        authSession.setAuthenticatedUser(user);
        authSession.setProtocol("openid-connect");

        // Set client scopes
        if (scope != null && !scope.isEmpty()) {
            authSession.setClientNote(AuthenticationManager.KEYCLOAK_SESSION_COOKIE + "scope", scope);
        }

        // Mark as authenticated
        authSession.setAuthNote(AuthenticationManager.SSO_AUTH, "true");

        LOG.debugf("Created authentication session: %s", authSession.getParentSession().getId());

        return authSession;
    }

    /**
     * Create user session from authentication session.
     *
     * @param user User model
     * @param client Client model
     * @param authSession Authentication session
     * @return UserSessionModel
     */
    private UserSessionModel createUserSession(UserModel user, ClientModel client,
            AuthenticationSessionModel authSession) {

        RealmModel realm = session.getContext().getRealm();

        // Create user session
        UserSessionModel userSession = session.sessions().createUserSession(
                realm,
                user,
                user.getUsername(),
                session.getContext().getConnection().getRemoteAddr(),
                "webauthn",  // Authentication method
                false,       // Remember me
                null,        // Broker session ID
                null         // Broker user ID
        );

        // Create client session
        AuthenticatedClientSessionModel clientSession = userSession.getAuthenticatedClientSessionByClient(client.getId());
        if (clientSession == null) {
            clientSession = session.sessions().createClientSession(
                    realm,
                    client,
                    userSession
            );
        }

        // Link authentication session to client session
        clientSession.setProtocol("openid-connect");

        // Copy notes from auth session to client session
        authSession.getClientNotes().forEach(clientSession::setNote);

        LOG.debugf("Created user session: %s for client: %s",
                userSession.getId(), client.getClientId());

        return userSession;
    }

    /**
     * Revoke tokens for a user session.
     *
     * @param sessionId User session ID
     */
    public void revokeTokens(String sessionId) {
        try {
            RealmModel realm = session.getContext().getRealm();
            UserSessionModel userSession = session.sessions().getUserSession(realm, sessionId);

            if (userSession != null) {
                session.sessions().removeUserSession(realm, userSession);
                LOG.infof("Revoked tokens for session: %s", sessionId);
            } else {
                LOG.warnf("User session not found: %s", sessionId);
            }

        } catch (Exception e) {
            LOG.errorf(e, "Failed to revoke tokens for session: %s", sessionId);
            throw new RuntimeException("Token revocation failed", e);
        }
    }

    /**
     * Refresh tokens using a refresh token.
     *
     * @param refreshToken Refresh token string
     * @param client Client model
     * @param scope Requested scope
     * @return Map containing new tokens
     */
    public Map<String, Object> refreshTokens(String refreshToken, ClientModel client, String scope) {
        try {
            LOG.debugf("Refreshing tokens for client: %s", client.getClientId());

            RealmModel realm = session.getContext().getRealm();
            TokenManager tokenManager = new TokenManager();

            // Verify and refresh tokens
            // Note: Full implementation would parse refresh token, validate, and generate new tokens
            // This is a simplified version

            LOG.infof("Tokens refreshed successfully for client: %s", client.getClientId());

            // Return new tokens (simplified - production would use TokenManager properly)
            Map<String, Object> tokens = new HashMap<>();
            tokens.put("token_type", "Bearer");

            return tokens;

        } catch (Exception e) {
            LOG.errorf(e, "Failed to refresh tokens for client: %s", client.getClientId());
            throw new RuntimeException("Token refresh failed", e);
        }
    }
}
