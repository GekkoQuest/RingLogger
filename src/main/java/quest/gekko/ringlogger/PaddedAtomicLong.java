package quest.gekko.ringlogger;

import java.util.concurrent.atomic.AtomicLong;

// https://github.com/ElevatedDev/RingBroker/blob/250ad78b5eb8793c6378ca15e370acfa7fb409fa/src/main/java/io/ringbroker/broker/ingress/Ingress.java#L211
public class PaddedAtomicLong extends AtomicLong {
    volatile long p1, p2, p3, p4, p5, p6, p7;

    public PaddedAtomicLong(long initialValue) {
        super(initialValue);
    }

    volatile long q1, q2, q3, q4, q5, q6, q7;
}