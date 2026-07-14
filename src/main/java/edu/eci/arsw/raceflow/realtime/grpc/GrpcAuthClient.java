package edu.eci.arsw.raceflow.realtime.grpc;

import edu.eci.arsw.raceflow.auth.grpc.ProfileRequest;
import edu.eci.arsw.raceflow.auth.grpc.ProfileResponse;
import edu.eci.arsw.raceflow.auth.grpc.UserProfileServiceGrpc;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Cliente del UserProfileService interno de auth-service. Permite que realtime-service resuelva
 * el nombre/deporte autoritativo del atleta en vez de confiar en lo que el frontend envía en el
 * cuerpo de la petición de crear/unirse a sala.
 */
@Component
public class GrpcAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GrpcAuthClient.class);
    private static final long CALL_TIMEOUT_SECONDS = 2;

    private final ManagedChannel ownedChannel;
    private final UserProfileServiceGrpc.UserProfileServiceBlockingStub stub;

    /**
     * @param host el host gRPC de auth-service (por defecto {@code localhost})
     * @param port el puerto gRPC de auth-service (por defecto {@code 9090})
     */
    @Autowired
    public GrpcAuthClient(@Value("${auth.grpc.host:localhost}") String host,
                           @Value("${auth.grpc.port:9090}") int port) {
        this.ownedChannel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.stub = UserProfileServiceGrpc.newBlockingStub(ownedChannel);
    }

    /**
     * Constructor solo para pruebas: permite que los tests inyecten un canal in-process en vez de
     * abrir un socket real.
     */
    GrpcAuthClient(Channel channel) {
        this.ownedChannel = null;
        this.stub = UserProfileServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Busca el perfil autoritativo de un atleta por email, con un límite de
     * 2 segundos. Los fallos (auth-service caído, timeout, etc.) se capturan
     * y se registran, devolviendo vacío para que quien llama pueda usar un valor de respaldo.
     *
     * @param email el email del atleta
     * @return el perfil, si auth-service encontró uno dentro del límite de tiempo
     */
    public Optional<ProfileResponse> lookupProfile(String email) {
        try {
            ProfileResponse response = stub
                    .withDeadlineAfter(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .getProfile(ProfileRequest.newBuilder().setEmail(email).build());
            return response.getFound() ? Optional.of(response) : Optional.empty();
        } catch (StatusRuntimeException e) {
            log.warn("gRPC call to auth-service UserProfileService failed for {}: {}", email, e.getStatus());
            return Optional.empty();
        }
    }

    /** Cierra ordenadamente el canal gRPC propio, si existe, al destruirse el bean. */
    @PreDestroy
    public void shutdown() {
        if (ownedChannel != null) {
            ownedChannel.shutdown();
        }
    }
}
