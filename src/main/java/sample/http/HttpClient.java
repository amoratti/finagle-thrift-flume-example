package sample.http;

import java.util.concurrent.TimeUnit;

import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;

import com.twitter.finagle.Service;
import com.twitter.finagle.builder.ClientBuilder;
import com.twitter.finagle.http.Http;
import com.twitter.util.Duration;
import com.twitter.util.FutureEventListener;
import com.twitter.util.Throw;
import com.twitter.util.Try;

public class HttpClient {

    public static void main(String[] args) {
        Service<HttpRequest, HttpResponse> client = ClientBuilder
                .safeBuild(ClientBuilder.get().codec(Http.get())
                        .hosts("localhost:10000").hostConnectionLimit(1));
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.GET, "/");
        
        // Async
        client.apply(request).addEventListener(
                new FutureEventListener<HttpResponse>() {
                    public void onSuccess(HttpResponse response) {
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException e) {
                            System.out.println(e.getLocalizedMessage());
                        }
                        System.out.println("received response: " + response.getContent().toString(
                                CharsetUtil.UTF_8));
                    }

                    public void onFailure(Throwable cause) {
                        System.out.println("failed with cause: " + cause);
                    }
                });
        
        // Sync with timeout
        HttpResponse response2 = client.apply(request).apply(new Duration(TimeUnit.SECONDS.toNanos(3)));
        System.out.println("received response2: " + response2.getContent().toString(
                CharsetUtil.UTF_8));

        // Sync with timeout and error handling
        Try<HttpResponse> responseTry = client.apply(request).get(new Duration(TimeUnit.SECONDS.toNanos(3)));
        if (responseTry.isReturn()) {
            // Cool, I have a response! Get it and do something
            System.out.println("received response3: " + responseTry.get().getContent().toString(
                    CharsetUtil.UTF_8));
        } else {
            // Throw an exception
            @SuppressWarnings("rawtypes")
            Throwable throwable = ((Throw) responseTry).e();
            System.out.println("Exception thrown by client: " + throwable);
        }
        
        client.release();
    }

}
