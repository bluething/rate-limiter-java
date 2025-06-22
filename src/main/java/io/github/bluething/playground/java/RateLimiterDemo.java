package io.github.bluething.playground.java;

public class RateLimiterDemo {
    static class TokenBucketLimiter {
        private final double capacity;
        private final double refillRatePerMs;
        private long lastRefillTime;
        private double currentCapacity;

        public TokenBucketLimiter(double capacity, double refillRate) {
            this.capacity = capacity;
            this.refillRatePerMs = refillRate / 1_000;
            currentCapacity = capacity;
            lastRefillTime = System.currentTimeMillis();
        }

        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            long delta = now - lastRefillTime;
            currentCapacity = Math.min(capacity, currentCapacity + delta * refillRatePerMs);
            lastRefillTime = now;

            if (currentCapacity >= 1) {
                currentCapacity--;
                return true;
            }

            return false;
        }
    }

    static void doWork(String name) {
        System.out.println("  ✔ " + name + " executed at " + System.currentTimeMillis());
    }
    public static void main(String[] args) throws InterruptedException {
        if (args.length != 1) {
            System.err.println("Please specify one of: FW | SW | TB | LB");
            System.exit(1);
        }

        String mode = args[0];
        switch (mode) {
            case "TB":
                TokenBucketLimiter limiter = new TokenBucketLimiter(3, 1);
                System.out.println("Testing TokenBucketLimiter:");
                for (int i = 0; i < 10; i++) {
                    if (limiter.tryAcquire()) {
                        doWork("TB");
                    } else {
                        System.out.println("  ✖ token rate limit");
                    }
                    Thread.sleep(1_000);
                }
                break;
            default:
                System.err.println("Unknown mode: " + mode);
                System.err.println("Valid modes: FW | SW | TB | LB");
                System.exit(1);
        }
    }
}
