/*
 * Copyright (c) 2012 Zauber S.A.  -- All rights reserved
 */
package sample.finagle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twitter.finagle.Service;
import com.twitter.finagle.SimpleFilter;
import com.twitter.util.Future;
import com.twitter.util.FutureTransformer;

/**
 * Logging filter  
 * 
 * 
 * @author Andrés Moratti
 * @since Aug 27, 2012
 */
public class LoggerFilter extends SimpleFilter<byte[], byte[]> {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    
    @Override
    public Future<byte[]> apply(byte[] request, Service<byte[], byte[]> service) {
        logger.warn("Request: {}", request);

        Future<byte[]> response;
        try {
            Future<byte[]> initialResponse = service.apply(request);
            response = initialResponse;
        } catch (Exception e) {
            response = Future.exception(e);
        }

        return response.transformedBy(new FutureTransformer<byte[], byte[]>() {

            @Override
            public byte[] map(byte[] response) {
                logger.warn("Response: {}", response);
                return response;
            }

            @Override
            public byte[] handle(Throwable throwable) {
                logger.warn("Service Exception: {}", throwable);
                return super.handle(throwable);
            }
        });
    }

}
