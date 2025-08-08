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

public class DebugTraceCall extends JsonRpc2_0Web3j implements Web3jTrace {

    public DebugTraceCall(Web3jService web3jService) {
        super(web3jService, DEFAULT_BLOCK_TIME, Async.defaultExecutorService());
    }

    public static DebugTraceCall build(Web3jService web3jService) {
        return new DebugTraceCall(web3jService);
    }

    public Request<?, DebugTraceCallResponse> traceCall(String from, String to, String data) {
        return traceCall(from, to, data, "callTracer", null);
    }

    public Request<?, DebugTraceCallResponse> traceCall(String from, String to, String data, String tracer) {
        return traceCall(from, to, data, tracer, null);
    }

    public Request<?, DebugTraceCallResponse> traceCall(String from, String to, String data, String tracer, Map<String, Object> tracerConfig) {
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
                "latest",
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
}