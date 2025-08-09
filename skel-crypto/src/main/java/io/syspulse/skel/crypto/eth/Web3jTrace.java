package io.syspulse.skel.crypto.eth;

import java.util.Map;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.JsonRpc2_0Web3j;

import io.syspulse.skel.crypto.eth.DebugTraceCallResponse;

public interface Web3jTrace extends Web3j {

    public static Web3jTrace build(Web3jService web3jService) {
        return new DebugTraceCall(web3jService);
    }

    Request<?, DebugTraceCallResponse> traceCall(String from, String to, String data, String tracer, Map<String, Object> tracerConfig);
}