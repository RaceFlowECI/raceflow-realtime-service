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
 * Client for auth-service's internal UserProfileService. Lets realtime-service resolve the
 * authoritative athlete name/sport instead of trusting whatever the frontend sends in the
 * create/join room request body.
 */
@Component
public class GrpcAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GrpcAuthClient.class);
    private static final long CALL_TIMEOUT_SECONDS = 2;

    private final ManagedChannel ownedChannel;
    private final UserProfileServiceGrpc.UserProfileServiceBlockingStub stub;

    /**
     * @param host auth-service's gRPC host (default {@code localhost})
     * @param port auth-service's gRPC port (default {@code 9090})
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
     * Test-only constructor: lets tests inject an in-process channel instead of opening a
     * real socket.
     */
    GrpcAuthClient(Channel channel) {
        this.ownedChannel = null;
        this.stub = UserProfileServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Looks up an athlete's authoritative profile by email, with a 2-second
     * deadline. Failures (auth-service down, timeout, etc.) are swallowed
     * and logged, returning empty so the caller can fall back gracefully.
     *
     * @param email the athlete's email
     * @return the profile, if auth-service found one within the deadline
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

    /** Gracefully shuts down the owned gRPC channel, if any, on bean destruction. */
    @PreDestroy
    public void shutdown() {
        if (ownedChannel != null) {
            ownedChannel.shutdown();
        }
    }
}
