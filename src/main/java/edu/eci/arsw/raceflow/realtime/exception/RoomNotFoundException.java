package edu.eci.arsw.raceflow.realtime.exception;

/** Thrown when a room code doesn't match any active in-memory room. */
public class RoomNotFoundException extends RuntimeException {
    public RoomNotFoundException(String message) {
        super(message);
    }
}
