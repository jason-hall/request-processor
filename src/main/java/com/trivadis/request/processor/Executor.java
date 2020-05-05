package com.trivadis.request.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class Executor {
    private final int responseSize;
    private final int requestCount;
    private final BlockingQueue<Request> requests;
    private final BlockingQueue<Response> responses;
    private final ExecutorService executorService;
    private final ArrayList<Long> responseTimes;
    private final AtomicBoolean stopWaiting = new AtomicBoolean();

    public Executor(int responseSize, int requestCount, int processingThreadCount) {
        this.responseSize = responseSize;
        this.requestCount = requestCount;
        requests = new ArrayBlockingQueue<>( 1, false );
        responses = new ArrayBlockingQueue<>( processingThreadCount, false );
        responseTimes = new ArrayList<>( requestCount );
        stopWaiting.set(false);

        new Thread( this::processResponse, "processResponse" ).start();
        new Thread( this::generateRequests, "generateRequests" ).start();
        executorService = Executors.newFixedThreadPool( processingThreadCount );
        IntStream.range( 0, processingThreadCount )
                .forEach( i -> executorService.execute( this::processRequest ) );
    }

    protected void generateRequests() {
        for ( int i = 0; i < requestCount; i++ ) {
            try {
                requests.put( new Request( i, Instant.now() ) );
            } catch ( InterruptedException e ) {
                e.printStackTrace();
                stopWaiting.set(true);
            }
            if (i == requestCount - 1 || 0 == i % (int)(requestCount * 0.05)) {
                System.out.print(".");
            }
        }
        System.out.println( "finished generateRequests " + Thread.currentThread().getName() );
    }

    protected void processRequest() {
        while ( true ) {
            try {
                Request request = requests.take();
                responses.put( createResponseAndGarbage( request ) );
            } catch ( InterruptedException e ) {
                stopWaiting.set(true);
                break;
            }
        }
        System.out.println( "finished processRequest " + Thread.currentThread().getName() );
    }

    private Response createResponseAndGarbage(Request request) {
        Instant start = request.time;
        Instant end = Instant.now();
        Duration duration = Duration.between( start, end );

        String valueStr = "";
        for ( int i = 0; i < responseSize; i++ ) {
            valueStr += (char) (32 + (request.index + i) % 95);
        }
        BigInteger hashCode = createHashCodeAndGarbage( valueStr );
        return new Response( request.index, start, end, duration, valueStr, hashCode );
    }

    private BigInteger createHashCodeAndGarbage(String s) {
        BigInteger hashCode = BigInteger.ZERO;
        for ( int v : s.toCharArray() ) {
            hashCode = hashCode.multiply( BigInteger.valueOf( 31 ) )
                    .add( BigInteger.valueOf( v ) )
                    .mod( BigInteger.valueOf( 1_000_000_000_000_000_000L ) );
        }
        return hashCode;
    }

    protected void processResponse() {
        // Wait until response received count matches expected request count.
        int numProcessed = 0;
        while ( numProcessed < requestCount ) {
            try {
                Response response = responses.poll(5, TimeUnit.SECONDS);
                // When a timeout occurs, break if an error condition was found.
                if ( response == null ) {
                     if ( stopWaiting.get() ) {
                          break;
                     }
                }
                else {
                     long duration = response.duration.toMillis();
                     responseTimes.add( duration );
                     numProcessed += 1;
                }
            } catch ( InterruptedException e ) {
                e.printStackTrace();
                stopWaiting.set(true);
            }
        }
        System.out.println( "finished processResponse " + Thread.currentThread().getName() + ". Procesed " + numProcessed );
        executorService.shutdownNow();
        dumpStatistics();
    }

    private void dumpStatistics() {
        int count = 0;
        Long sum = 0L;
        Long min = 0L;
        Long max = 0L;
        Collections.sort(responseTimes);
        try ( PrintWriter out = new PrintWriter( Files.newBufferedWriter( Paths.get( "response_times.txt" ) ) ) ) {
            for ( Long time : responseTimes ) {
                out.println( time );
                count += 1;
                sum += time;
                if ( time <= min || min == 0 ) {
                    min = time;
                }
                if ( time >= max || max == 0 ) {
                    max = time;
                }
            }
            String output = "Response time: average " + (sum / count) + ", median " + responseTimes.get((int)(count / 2)) + ", minimum " + min + ", maximum " + max + ".";
            out.println(output);
            System.out.println(output);
        } catch ( IOException e ) {
            throw new UncheckedIOException( e );
        }
    }


    static class Request {
        final int index;
        Instant time;

        Request(int index, Instant time) {
            this.index = index;
            this.time = time;
        }
    }

    static class Response {
        final int index;
        final Instant start;
        final Instant end;
        final Duration duration;
        final String valueStr;
        final BigInteger hashCode;

        Response(int index, Instant start, Instant end, Duration duration, String valueStr, BigInteger hashCode) {
            this.index = index;
            this.start = start;
            this.end = end;
            this.duration = duration;
            this.valueStr = valueStr;
            this.hashCode = hashCode;
        }

        @Override
        public String toString() {
            return "Response{" +
                    "index=" + index +
                    ", start=" + start +
                    ", end=" + end +
                    ", duration=" + duration +
                    ", valueStr='" + valueStr + '\'' +
                    ", hashCode=" + hashCode +
                    '}';
        }
    }
}
