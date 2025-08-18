package com.concerto.omnichannel.connector;

import com.concerto.omnichannel.configManager.ConnectorTimeoutConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Component("ISO8583")
public class ISO8583Connector implements Connector {
    @Autowired
    private ConnectorTimeoutConfig timeoutConfig;

    @Override
    public String process(String payload) throws Exception {
        // Add ISO8583 request building logic here
        System.out.println("Processing ISO8583 payload: " + payload);
      //  return "{ \"isoStatus\": \"APPROVED\", \"authCode\": \"123456\" }";
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Callable<String> task = () -> {
            Thread.sleep(2000);
            return "{ \"isoStatus\": \"APPROVED\", \"authCode\": \"123456\" }";
        };

        Future<String> future = executor.submit(task);
        try{
            int timeout = timeoutConfig.getTimeoutFor("ISO8583");
            return future.get(timeout, TimeUnit.MILLISECONDS);
        }catch (TimeoutException exception){
            future.cancel(true);
            throw new RuntimeException("ISO8583 Connector timed out");
        }finally {
            executor.shutdownNow();
        }
    }
}

