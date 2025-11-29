package com.inventage.keycloak.webauthn.infrastructure.provider;

import com.google.auto.service.AutoService;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

/**
 * Factory for creating WebAuthn realm resource providers.
 * <p>
 * This factory is automatically registered with Keycloak using the {@link AutoService}
 * annotation. It creates {@link WebAuthnRealmResourceProvider} instances that expose
 * REST endpoints for WebAuthn credential registration and authentication.
 * </p>
 * <p>
 * The provider exposes endpoints under:
 * {@code /realms/{realm}/api/webauthn/*}
 * </p>
 *
 * @see WebAuthnRealmResourceProvider
 * @see RealmResourceProviderFactory
 * @since 1.0.0
 */
@AutoService(RealmResourceProviderFactory.class)
public class WebAuthnRealmResourceProviderFactory implements RealmResourceProviderFactory {

    /**
     * The unique provider ID used to identify this resource provider.
     * This ID is used in the URL path: /realms/{realm}/api/{PROVIDER_ID}
     */
    public static final String PROVIDER_ID = "webauthn";

    /**
     * Creates a new instance of the WebAuthn realm resource provider.
     * <p>
     * This method is called by Keycloak for each request to the provider's endpoints.
     * The created provider instance handles the REST API requests for WebAuthn operations.
     * </p>
     *
     * @param session The Keycloak session for this request
     * @return A new {@link WebAuthnRealmResourceProvider} instance
     */
    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new WebAuthnRealmResourceProvider(session);
    }

    /**
     * Initializes the provider factory with configuration.
     * <p>
     * This method is called once during Keycloak startup. Configuration can be
     * provided via the Keycloak configuration file (e.g., standalone.xml or
     * standalone-ha.xml).
     * </p>
     *
     * @param config The configuration scope for this provider
     */
    @Override
    public void init(Config.Scope config) {
        // Initialize provider configuration if needed
        // Example: Read timeout values, RP ID, allowed origins, etc.
    }

    /**
     * Post-initialization hook called after all providers are initialized.
     * <p>
     * This method is called after all provider factories have been initialized,
     * allowing for cross-provider initialization logic if needed.
     * </p>
     *
     * @param factory The Keycloak session factory
     */
    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Post-initialization hook for additional setup
        // Example: Register event listeners, initialize shared resources
    }

    /**
     * Cleanup resources when the provider factory is shut down.
     * <p>
     * This method is called during Keycloak shutdown to clean up any resources
     * allocated by this factory.
     * </p>
     */
    @Override
    public void close() {
        // Cleanup resources if needed
        // Example: Close connection pools, cleanup temporary files
    }

    /**
     * Gets the unique identifier for this provider.
     * <p>
     * This ID is used in the URL path structure and must be unique across all
     * realm resource providers in the Keycloak instance.
     * </p>
     *
     * @return The provider ID "webauthn"
     */
    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
