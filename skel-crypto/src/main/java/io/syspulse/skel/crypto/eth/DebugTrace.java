package io.syspulse.skel.crypto.eth;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.utils.Async;
import org.web3j.protocol.core.JsonRpc2_0Web3j;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DebugTrace extends JsonRpc2_0Web3j implements Web3jTrace {

    public DebugTrace(Web3jService web3jService) {
        super(web3jService, DEFAULT_BLOCK_TIME, Async.defaultExecutorService());
    }

    public static DebugTrace build(Web3jService web3jService) {
        return new DebugTrace(web3jService);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(" + this.getWeb3jService() + ")";
    }
    
    public Web3jService getWeb3jService() {
        return this.web3jService;
    }

    public Request<?, DebugTraceCallResponse> traceCall(String from, String to, String data) {
        return traceCall(from, to, data, "callTracer", null,"latest");
    }

    public Request<?, DebugTraceCallResponse> traceCall(String from, String to, String data, String tracer) {
        return traceCall(from, to, data, tracer, null,"latest");
    }

    public Request<?, DebugTraceCallResponse> traceCall(String from, String to, String data, String tracer, Map<String, Object> tracerConfig) {
        return traceCall(from, to, data, tracer, tracerConfig,"latest");
    }

    public Request<?, DebugTraceCallResponse> traceCall(String from, String to, String data, String tracer, Map<String, Object> tracerConfig,String blockNumber) {
        // Build the transaction object (eth_call params)
        Map<String, String> callObject = new HashMap<>();
        callObject.put("from", from);
        callObject.put("to", to);
        callObject.put("data", data);

        // tracers:
        // callTracer
        // prestateTracer        

        // Build options with optional tracerConfig
        Map<String, Object> options = new HashMap<>();
        options.put("tracer", tracer);
        if (tracerConfig != null) {
            options.put("tracerConfig", tracerConfig);
        }

        // Parameters: [callObject, blockNumber, options]
        Object[] params = new Object[]{
                callObject,
                blockNumber,
                options
        };

        Request<?, DebugTraceCallResponse> request = new Request<>(
                "debug_traceCall",
                Arrays.asList(params),
                this.web3jService,
                DebugTraceCallResponse.class


        );

        return request;
    }

    /// ==========================================================================================================================
    public Request<?, DebugTraceTransactionResponse> traceTransaction(String tx) {
        return traceTransaction(tx, "callTracer", null);
    }

    public Request<?, DebugTraceTransactionResponse> traceTransaction(String tx, String tracer) {
        return traceTransaction(tx, tracer, null);
    }
    
    public Request<?, DebugTraceTransactionResponse> traceTransaction(String tx, String tracer, Map<String, Object> tracerConfig) {
        
        // tracers:
        // callTracer
        // prestateTracer        

        // Build options with optional tracerConfig
        Map<String, Object> options = new HashMap<>();
        options.put("tracer", tracer);
        if (tracerConfig != null) {
            options.put("tracerConfig", tracerConfig);
        }

        // Parameters: [callObject, blockNumber, options]
        Object[] params = new Object[]{
                tx,
                options
        };

        Request<?, DebugTraceTransactionResponse> request = new Request<>(
                "debug_traceTransaction",
                Arrays.asList(params),
                this.web3jService,
                DebugTraceTransactionResponse.class


        );

        return request;
    }
}