package sample.thrift;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twitter.finagle.Service;
import com.twitter.finagle.builder.ClientBuilder;
import com.twitter.finagle.stats.SummarizingStatsReceiver;
import com.twitter.finagle.thrift.ThriftClientFramedCodec;
import com.twitter.finagle.thrift.ThriftClientRequest;
import com.twitter.util.Duration;
import com.twitter.util.FutureEventListener;

public class ThriftClient {

    private static final Logger logger = LoggerFactory.getLogger(ThriftClient.class);
    
    public static void main(String[] args) {

        final SummarizingStatsReceiver statsReceiver = new SummarizingStatsReceiver();
        
        // IMPORTANT: this determines how many rpc's are sent in at once.
        // If set to 1, you get no parallelism on for this client.
        final Service<ThriftClientRequest, byte[]> client = ClientBuilder.safeBuild(ClientBuilder.get()
                .hosts(new InetSocketAddress(8080))
                .codec(ThriftClientFramedCodec.get())
                .hostConnectionLimit(2)
                .tcpConnectTimeout(new Duration(1 * Duration.NanosPerSecond()))
                .retries(2)
                .reportTo(statsReceiver));

        final Hello.ServiceIface helloClient = new Hello.ServiceToClient(client,
                new TBinaryProtocol.Factory());

        // Simple call to ask the server to say hi.
        helloClient.hi().addEventListener(new FutureEventListener<String>() {
            @Override
            public void onFailure(Throwable cause) {
                logger.info("Hi call. Failure: " + cause);
            }

            @Override
            public void onSuccess(String value) {
                logger.info("Hi call. Success: " + value);
            }
        });

        // Simple call to as the server to add a couple numbers
        helloClient.add(40, 2).addEventListener(new FutureEventListener<Integer>() {
            @Override
            public void onSuccess(Integer integer) {
                logger.info("Add call success. Answer: " + integer);
            }

            @Override
            public void onFailure(Throwable throwable) {
                logger.info("Add call fail because: " + throwable);
            }
        });

        // Now let's inundate the server with lots of blocking calls and watch as it handles them
        final int numCalls = 100;
        final List<BlockingCallResponse> responses = new ArrayList<BlockingCallResponse>(numCalls);

        for (int i = 0; i < numCalls; i++) {
            final BlockingCallResponse blockingCallResponse = new BlockingCallResponse(i);

            // Send call to the server, return its result handler
            helloClient.blocking_call().addEventListener(blockingCallResponse);
            responses.add(blockingCallResponse);
            logger.info("Queued up request #" + i);
            
            // Just for fun, throw in some non-blocking calls to ensure the
            // server responds quickly.
            helloClient.add(i, i).addEventListener(new FutureEventListener<Integer>() {
                @Override
                public void onSuccess(Integer integer) {
                    logger.info("Extra Add call success. Answer: " + integer);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    logger.info("Extra Add call fail because: " + throwable);
                }
            });
        }
        
        
        logger.warn("Waiting until everyone is done");
        boolean done = false;

        while (!done) {
            // Check to see how many results we've received, report on the
            // number
            int count = 0;
            for (BlockingCallResponse blockingCallResponse : responses) {
                if (blockingCallResponse.isDone()) count++;

                done = true;
            }

            // We're done when everyone has gotten a result back
            done = count == numCalls;
            if (!done) {
                try {
                    Thread.sleep(1 * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        // Close down the client
        client.release(); 
        
//        logger.warn("Everybody is done, let's see what they got: ");
//        for (BlockingCallResponse blockingCallResponse : responses) {
//            logger.info("Answer = " + blockingCallResponse.getAnswer());
//        }

        logger.warn("Done");
        return;
    }

    public static class BlockingCallResponse implements FutureEventListener<Integer> {
        // this was call number
        final int num; 

        public BlockingCallResponse(int num) {
            this.num = num;
        }

        // Have we got a response yet?
        final AtomicBoolean b = new AtomicBoolean(false);
        Integer answer = null;

        public void onFailure(Throwable cause) {
            b.set(true);
            logger.info("Failure in BlockingCallResponse for #" + num + ": " + cause);
        }

        public void onSuccess(Integer value) {
            answer = value;
            b.set(true);
            logger.info("Got a response back for #" + num + ": " + value);
        }

        public int getAnswer() {
            return answer;
        }

        public boolean isDone() {
            return b.get();
        }
    }
}