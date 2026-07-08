package edu.eci.arsw.raceflow.realtime.grpc;

import edu.eci.arsw.raceflow.auth.grpc.ProfileRequest;
import edu.eci.arsw.raceflow.auth.grpc.ProfileResponse;
import edu.eci.arsw.raceflow.auth.grpc.UserProfileServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises GrpcAuthClient against a real (in-process, no network socket) gRPC server, so this
 * proves the wire format and stub wiring work, not just that the code compiles.
 */
class GrpcAuthClientTest {

    private final List<Server> servers = new ArrayList<>();
    private final List<ManagedChannel> channels = new ArrayList<>();

    @AfterEach
    void tearDown() throws InterruptedException {
        for (ManagedChannel channel : channels) {
            channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        }
        for (Server server : servers) {
            server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private GrpcAuthClient startClientAgainst(UserProfileServiceGrpc.UserProfileServiceImplBase serviceImpl) throws Exception {
        String serverName = "grpc-auth-client-test-" + System.nanoTime();

        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(serviceImpl)
                .build()
                .start();
        servers.add(server);

        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        channels.add(channel);

        return new GrpcAuthClient(channel);
    }

    @Test
    void lookupProfileReturnsProfileWhenFound() throws Exception {
        GrpcAuthClient client = startClientAgainst(new UserProfileServiceGrpc.UserProfileServiceImplBase() {
            @Override
            public void getProfile(ProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
                responseObserver.onNext(ProfileResponse.newBuilder()
                        .setFound(true).setEmail(request.getEmail()).setName("Juan Perez").setSport("ciclismo")
                        .build());
                responseObserver.onCompleted();
            }
        });

        Optional<ProfileResponse> result = client.lookupProfile("juan@raceflow.dev");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Juan Perez");
    }

    @Test
    void lookupProfileReturnsEmptyWhenNotFound() throws Exception {
        GrpcAuthClient client = startClientAgainst(new UserProfileServiceGrpc.UserProfileServiceImplBase() {
            @Override
            public void getProfile(ProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
                responseObserver.onNext(ProfileResponse.newBuilder().setFound(false).build());
                responseObserver.onCompleted();
            }
        });

        assertThat(client.lookupProfile("ghost@raceflow.dev")).isEmpty();
    }

    @Test
    void lookupProfileReturnsEmptyWhenServerErrors() throws Exception {
        GrpcAuthClient client = startClientAgainst(new UserProfileServiceGrpc.UserProfileServiceImplBase() {
            @Override
            public void getProfile(ProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
                responseObserver.onError(Status.UNAVAILABLE.withDescription("auth-service is down").asRuntimeException());
            }
        });

        assertThat(client.lookupProfile("juan@raceflow.dev")).isEmpty();
    }

    @Test
    void shutdownIsNoOpForClientBuiltFromAnExternalChannel() throws Exception {
        GrpcAuthClient client = startClientAgainst(new UserProfileServiceGrpc.UserProfileServiceImplBase() {
        });

        client.shutdown(); // should not throw even though this client doesn't own the channel
    }

    @Test
    void productionConstructorBuildsAndShutsDownOwnedChannelWithoutConnecting() {
        // ManagedChannelBuilder is lazy: building it does not open a socket until the first RPC,
        // so this is safe to run without a real auth-service listening on the port.
        GrpcAuthClient client = new GrpcAuthClient("localhost", 9090);

        client.shutdown();
    }
}
