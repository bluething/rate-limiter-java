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







