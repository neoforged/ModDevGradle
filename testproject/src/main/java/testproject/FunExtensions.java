package testproject;

public interface FunExtensions {
    default String testmodThisIsMine() {
        return "Hello World " + toString();
    }
}
