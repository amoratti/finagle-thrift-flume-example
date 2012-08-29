package sample.thrift;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twitter.finagle.Service;
import com.twitter.finagle.builder.ServerBuilder;
import com.twitter.finagle.thrift.ThriftServerFramedCodec;
import com.twitter.util.Duration;
import com.twitter.util.ExecutorServiceFuturePool;
import com.twitter.util.Function0;
import com.twitter.util.Future;

public class ThriftServer {

    private static final Logger logger = LoggerFactory.getLogger(ThriftServer.class);
    
    
    // Function0 implementations - how to do blocking work on the server:
    // In Scala, Finagle passes closures to to a threadpool to execute long-running calls on another thread.
    // In Java, the closure is simulated by passing an instance of a Function0 (which is a wrapper around
    // how Scala actually implements closures, and should probably be named something more Java friendly).
    // The only method to implement is apply, which is where the long-running calculation lines.
    // This example just burns through CPU for the specified number of seconds, simulating some Big Important Work
    public static class ExampleBlockingCall extends com.twitter.util.Function0<Integer> {
      private int delayForSeconds;

      public ExampleBlockingCall(int delayForSeconds) {
        this.delayForSeconds = delayForSeconds;
      }

      public Integer apply() {
        logger.warn("Blocking call will now do some busy work for " + delayForSeconds + " seconds");
        
        long delayUntil = System.currentTimeMillis() + (delayForSeconds * 1000);
        long acc = 0;
        while(System.currentTimeMillis() < delayUntil) {
          // Let's bind and gag the CPU
          for(int i = 0; i < 1000; i++) {
              for(int j = 0; j < 1000; j++) {
                  acc += delayForSeconds + j + i;
              }
          }
        }

        final int returnValue = acc == System.currentTimeMillis() ? 42 : delayForSeconds;
        
        logger.warn("Blocking call returns " + returnValue);
        
        return returnValue; // whatever, doesn't matter
      }
    }
    
    /**
     * Special Interface added by Finagle's thrift compiler  
     */
    public static class HelloServer implements Hello.ServiceIface {
        // In Scala, one can call directly to the FuturePool, but Java gets
        // confused
        // between the object and class, so it's best to instantiate an
        // ExecutorServiceFuturePool directly

        // Number of threads to devote to blocking requests
        final ExecutorService es = Executors.newFixedThreadPool(6);
        // Pool to process blockng requests so server thread doesn't
        final ExecutorServiceFuturePool esfp = new ExecutorServiceFuturePool(es);

        final Random r = new Random();

        // Simple call that returns a value
        @Override
        public Future<String> hi() {
            logger.info("HelloServer:hi request received.");
            // Future.value is an immediate return of the expression, suitable for non-blocking calls
            return Future.value("Hello, Stonehenge! At the beep, the time will be "
                    + System.currentTimeMillis());
        }

        // Very fast, non-blocking computation that the server can respond to immediately
        @Override
        public Future<Integer> add(int a, int b) {
            logger.info("HelloServer:add(" + a + " ," + b + ") request received");
            // Future.value is an immediate return of the expression, suitable for non-blocking calls
            return Future.value(a + b);
        }

        // Call that will take some time and should be moved off of the server's main event loop
        @Override
        public Future<Integer> blocking_call() {
            final int delay = r.nextInt(5); // blocking calls will take between 0 and 5 seconds
            logger.info("HelloServer:blocking_call requested. Will block for " + delay + " seconds");
            Function0<Integer> blockingWork = new ExampleBlockingCall(delay);
            // Load the blocking call on the threadpool to be scheduled and
            // eventually executed. Once complete,
            // the result will be returned to the client
            return esfp.apply(blockingWork);
        }
    }
    
    /**
     * @param args
     */
    public static void main(String[] args) {

        final Hello.ServiceIface processor = new HelloServer();
//        final LoggerFilter loggerFilter = new LoggerFilter();
        final Service<byte[], byte[]> service = new Hello.Service(processor, new TBinaryProtocol.Factory());
        
        ServerBuilder.safeBuild(
                service,
//                loggerFilter.andThen(service), 
                ServerBuilder.get()
                    .name("HelloService")
                    .codec(ThriftServerFramedCodec.get())
                    .bindTo(new InetSocketAddress(8080))
                    .readTimeout(new Duration(2 * Duration.NanosPerSecond())));
    }
}
