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





