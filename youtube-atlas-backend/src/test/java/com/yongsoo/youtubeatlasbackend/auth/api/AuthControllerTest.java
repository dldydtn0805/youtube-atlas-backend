package com.yongsoo.youtubeatlasbackend.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.yongsoo.youtubeatlasbackend.auth.AuthService;
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;

class AuthControllerTest {

    @Test
    void getGoogleAuthConfigEnablesLoginWhenClientIdExists() {
        AtlasProperties atlasProperties = new AtlasProperties();
        atlasProperties.getAuth().setGoogleClientId(" google-client-id ");
        atlasProperties.getAuth().setGoogleClientSecret("");

        AuthController controller = new AuthController(mock(AuthService.class), atlasProperties);

        GoogleAuthConfigResponse response = controller.getGoogleAuthConfig();

        assertThat(response.clientId()).isEqualTo("google-client-id");
        assertThat(response.enabled()).isTrue();
    }

    @Test
    void getGoogleAuthConfigDisablesLoginWhenClientIdMissing() {
        AtlasProperties atlasProperties = new AtlasProperties();
        atlasProperties.getAuth().setGoogleClientId(" ");
        atlasProperties.getAuth().setGoogleClientSecret("google-client-secret");

        AuthController controller = new AuthController(mock(AuthService.class), atlasProperties);

        GoogleAuthConfigResponse response = controller.getGoogleAuthConfig();

        assertThat(response.clientId()).isEmpty();
        assertThat(response.enabled()).isFalse();
    }
}
