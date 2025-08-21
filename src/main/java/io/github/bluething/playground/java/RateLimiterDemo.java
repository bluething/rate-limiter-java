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

    static class LeakyBucketLimiter {
        private long nextAllowedTime;
        private final long intervalInMillis;

        LeakyBucketLimiter(int callsPerSecond) {
            this.nextAllowedTime = System.currentTimeMillis();
            this.intervalInMillis = 1_000L / callsPerSecond;
        }

        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now >= nextAllowedTime) {
                nextAllowedTime = now + intervalInMillis;
                return true;
            }

            return false;
        }
    }

    static class BoundedLeakyBucketLimiter {
        private final long capacity;
        private final long leakIntervalMs;
        private long lastCheckTime;
        private long currentCapacity;


        BoundedLeakyBucketLimiter(long capacity, int callsPerSecond) {
            this.capacity = capacity;
            this.leakIntervalMs = 1_000L / callsPerSecond;
        }

        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastCheckTime;
            long leaked = elapsed / leakIntervalMs;
            // leak out as much as we can since last check
            if (leaked > 0) {
                currentCapacity = Math.max(0, currentCapacity - leaked);
                lastCheckTime += leaked * leakIntervalMs;
            }
            // try to pour one more unit in
            if (currentCapacity < capacity) {
                currentCapacity++;
                return true;
            }
            // bucket is full → overflow
            return false;
        }
    }

    static class FixedWindowLimiter {
        private static class Window {
            long start;
            long count;
        }
        private final Window window = new Window();
        private final int maxCalls;
        private final long windowSize;

        FixedWindowLimiter(int maxCalls, long windowSize) {
            this.maxCalls = maxCalls;
            this.windowSize = windowSize;
        }

        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - window.start >= windowSize) {
                window.start = now;
                window.count = 1;
                return true;
            }
            if (window.count < maxCalls) {
                window.count++;
                return true;
            }
            return false;
        }
    }

    static class SlidingWindowCounterLimiter {
        private final int maxCalls;
        private final long windowSize;
        private long windowStart;
        private int currentCount;
        private int previousCount;

        SlidingWindowCounterLimiter(int maxCalls, long windowSize) {
            this.maxCalls = maxCalls;
            this.windowSize = windowSize;
            this.windowStart = (System.currentTimeMillis() / windowSize) * windowSize;
        }

        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            long newWindowStart = (now / windowSize ) * windowSize;
            if (windowStart < newWindowStart) {
                if (newWindowStart - windowStart >= 2 * windowSize) {
                    previousCount = 0;
                } else {
                    previousCount = currentCount;
                }
                currentCount = 0;
                windowStart = newWindowStart;
            }

            double elapsed = now - windowStart;
            double weight = (windowSize / elapsed) * windowSize;
            double estimate = currentCount + previousCount * weight;
            if (estimate < maxCalls){
                currentCount++;
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

            case "LB":
                LeakyBucketLimiter leakyBucketLimiter = new LeakyBucketLimiter(1);
                System.out.println("Testing LeakyBucketLimiter:");
                for (int i = 0; i < 10; i++) {
                    if (leakyBucketLimiter.tryAcquire()) {
                        doWork("LB");
                    } else {
                        System.out.println("  ✖ leaky rate limit");
                    }
                    Thread.sleep(500);
                }
                break;
            case "BLB":
                BoundedLeakyBucketLimiter boundedLeakyBucketLimiter = new BoundedLeakyBucketLimiter(3, 1);
                System.out.println("Testing BoundedLeakyBucketLimiter:");
                for (int i = 0; i < 10; i++) {
                    if (boundedLeakyBucketLimiter.tryAcquire()) {
                        doWork("BLB");
                    } else {
                        System.out.println("  ✖ bounded leaky rate limit");
                    }
                    Thread.sleep(500);
                }
                break;
            case "FW":
                FixedWindowLimiter fwdLimiter = new FixedWindowLimiter(3, 5_000);
                System.out.println("Testing FixedWindowLimiter:");
                for (int i = 0; i < 10; i++) {
                    if (fwdLimiter.tryAcquire()) {
                        doWork("FW");
                    } else {
                        System.out.println("  ✖ fixed window rate limit");
                    }
                    Thread.sleep(1_000);
                }
                break;
            case "SW":
                SlidingWindowCounterLimiter swLimiter = new SlidingWindowCounterLimiter(3, 5_000);
                System.out.println("Testing SlidingWindowCounterLimiter:");
                for (int i = 0; i < 10; i++) {
                    if (swLimiter.tryAcquire()) {
                        doWork("SW");
                    } else {
                        System.out.println("  ✖ sliding window rate limit");
                    }
                    Thread.sleep(1_000);
                }
                break;
            default:
                System.err.println("Unknown mode: " + mode);
                System.err.println("Valid modes: FW | SW | TB | LB | BLB");
                System.exit(1);
        }
    }
}
