package com.quhealthy.auth_service.service.security;

import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient;
import com.restfb.Parameter;
import com.restfb.Version;
import com.restfb.types.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FacebookAuthService {

    /**
     * Valida el token recibiendo desde el front y obtiene los datos del usuario.
     * @param accessToken El token que nos da el SDK de Facebook en el front.
     * @return Objeto User de RestFB con email, nombre e ID.
     */
    public User getUserData(String accessToken) {
        try {
            // Instanciamos el cliente con la versión más reciente de la API
            FacebookClient facebookClient = new DefaultFacebookClient(accessToken, Version.LATEST);

            // Solicitamos campos específicos: ID, Nombre, Email y Foto
            User user = facebookClient.fetchObject("me", User.class, 
                    Parameter.with("fields", "id,name,email,picture"));

            if (user == null || user.getId() == null) {
                throw new RuntimeException("Token de Facebook inválido o expirado.");
            }

            // OJO: Facebook permite crear cuentas con celular. Si el usuario no tiene email,
            // esto vendrá null. Debemos manejarlo en el AuthService.
            return user;

        } catch (Exception e) {
            log.error("❌ Error comunicándose con Facebook Graph API: {}", e.getMessage());
            throw new RuntimeException("Error al validar token de Facebook.");
        }
    }
}