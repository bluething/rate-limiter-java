### Rate Limiter Algorithm  
#### Token Bucket
The Token Bucket is a simple, yet powerful, rate-limiting algorithm that controls the amount and burstiness of requests. Here’s how it works:
1. Bucket with Tokens  
   - You have a “bucket” that can hold up to capacity tokens (e.g. 100).  
   - Tokens represent _permission_ to do work (e.g. accept one request).  
2. Refill at a Steady Rate  
   - Tokens are added to the bucket at a constant refill rate (e.g. 5 tokens/sec).  
   - If the bucket is already full, extra tokens are discarded (it never exceeds capacity).
3. On Each Request  
   - You check how many tokens are in the bucket.  
   - If ≥1 token is available, you remove one token and allow the request.  
   - If 0 tokens remain, you reject (or queue/delay) the request.
4. Bursty vs. Steady Traffic  
    - Bursty bursts up to the bucket’s capacity are allowed instantly (e.g. a sudden spike of 50 requests can be served if the bucket has 50 tokens saved up).  
    - Sustained high rates beyond the refill rate will exhaust the bucket and start getting throttled, smoothing out long-term throughput.

Why It’s Useful  
* Flexibility: You can tune both the maximum burst (capacity) and the long-term rate (refill rate) independently.  
* Fairness: All clients share the same refill logic, so no one can exceed sustained capacity.  
* Efficiency: It only needs a small amount of state (current token count and last refill timestamp) and can be implemented in constant time.

In the implementation we use refillRatePerMs because our clock (System.currentTimeMillis()) measures time in ms then we divide refillRate (in second) by 1000 because we want to convert tokens-per-second into tokens-per-millisecond.

Real-World Uses  
* API gateways and microservices to protect downstream systems.  
* Network traffic shaping in routers (packets are “tokens”).  
* Any scenario where you want to enforce both a hard cap on burst size and a soft cap on sustained rate.

#### Token Bucket
The Leaky Bucket is a rate-limiting algorithm that smooths out bursts by enforcing a constant “drip” of work. Imagine you have a bucket with a small hole in the bottom:  
1. Incoming Work = Water Pour  
   - Every time a request arrives, you pour one unit of water into the bucket.  
2. Steady Leak = Constant Service Rate  
   - The bucket leaks (empties) at a fixed rate—say, one drop every 10 ms.  
   - That leak represents your system’s capacity to handle work steadily.  
3. If Bucket Overflows = Throttle / Drop  
   - The bucket has a finite size (capacity).  
   - If too much water is poured in too quickly—faster than it can leak—the bucket overflows and excess water is discarded.  
   - In practice, that means you reject or delay any request that arrives when the bucket is already full.

Key Characteristics  
* Fixed Output Rate  
   Work exits (leaks) at exactly one pace—no bursts get through faster than the leak rate.  
* Burst Absorption up to Capacity  
  Short spikes of volume can be absorbed (up to the bucket’s size), but anything beyond that is dropped immediately.  
* Simple State  
* You only need to track:  
    - nextAllowedTime (or equivalently, current “water level”)  
    - interval between leaks (derived from desired rate)  
* Use Cases  
    - Enforcing a hard cap on how fast you can process events (e.g., packet scheduling, fixed-rate APIs).  
    - Smoothing out bursty input so that downstream systems always see a steady, predictable load.

Comparison to Token Bucket

|  Aspect |  Leaky Bucket | Token Bucket  |
|---|---|---|
| **Output Rate**  | Fixed (strict pacing)  |  Variable (up to capacity, then paced) |
| **Burst Handling**  | Limited to bucket size; excess dropped immediately  | You accumulate tokens when idle and then drain as a burst  |
| **Common Use**  | Enforcing a hard, steady rate  | Allowing controlled bursts + long-term rate  |

I create two implementation   
1. LeakyBucketLimiter -> unlimited capacity  
2. BoundedLeakyBucketLimiter -> limited capacity

#### Fixed Window

The Fixed Window algorithm divides time into consecutive, non-overlapping intervals (“windows”) of a fixed length (e.g. 60 seconds). You simply count how many requests fall into the current window, and once you hit your limit, you block any further requests until the next window begins.  

How it works  
1. Window tracking  
    - Keep a record of the start timestamp of the active window (windowStart).  
    - Track a counter of requests seen in that window (count).  
2. On each request  
    1. Compute now = currentTimeMillis().  
    2. If now – windowStart ≥ windowSize, reset the window:  
        - windowStart = now  
        - count = 1 (this request)  
        - Allow the request.  
3. Otherwise, (we’re still in the same window):  
    - If count < maxCalls: increment count and allow.  
    - Else: reject (429 Too Many Requests).

Characteristics  
* Simple: only two variables (start + count), O(1) per request.  
* Bursty edges: at the boundary you can get a “double burst”—one full window of calls just before the reset, then another full window immediately after.  
* Accuracy: coarse-grained; may under- or over-throttle by up to one window’s worth.

Pros & Cons   

| Pros                                  | Cons                                                     |
| ------------------------------------- | -------------------------------------------------------- |
| Very easy to implement & reason about | Two adjacent windows can each hit full limit → spikes    |
| Constant-time checks, minimal memory  | Doesn’t smooth traffic; sharp on/off throttling behavior |
| No per‐request list or sliding math   | Inaccurate around window edges                           |

When to use  
* You need very low overhead and can tolerate occasional “double bursts.”  
* Your load pattern is fairly even, or you already have other smoothing mechanisms in place.  
* You want a quick PoC before moving to more precise approaches (sliding window, token bucket, etc.).

#### Sliding Window Counter

How It Works  
1. Two buckets  
    - Current window counter (currentCount) for the interval [T, T + W)  
    - Previous window counter (previousCount) for the interval [T − W, T)  
2. Window alignment  
    - Compute the start of the “current” window as  
        `windowStart = (now / W) * W;`  
    - If you’ve crossed into a new window since the last request, shift:  
        - If you jumped more than one window, zero out previousCount  
        - Else move currentCount → previousCount  
        - Reset currentCount = 0  
3. Weighted estimate  
    - Calculate how far we are into the window:  
        `elapsed = now − windowStart;  
        weight  = (W − elapsed) / W;`  
    - Estimate total requests in the last W-ms span as:  
        `estimate = currentCount + previousCount * weight`  
    - If estimate < limit, allow (and currentCount++), else reject.

```text
Time →  |---- Minute 1 ----|---- Minute 2 ----|---- Minute 3 ----|
Calls   | ● ● ● ● ● ● ● ●   | ● ● ● ● ●        |                 
```
* In Minute 1, you made 8 calls (8 marbles).  
* In Minute 2 (halfway), you’ve already made 5 calls.

How Sliding Window Counter works halfway through Minute 2  
1. We ask: “How many marbles do we count for the last 60 seconds?”  
2. From Minute 2: take all 5 marbles (because they’re in this minute).  
3. From Minute 1: take only half of its marbles (because we’re halfway into Minute 2).   
4. Half of 8 = 4 marbles.  
5. 👉 Total = 5 (current) + 4 (weighted previous) = 9 marbles. 
6. Still under the limit (10) → Allowed ✅