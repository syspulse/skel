package io.syspulse.skel.crypto.eth;

import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.DefaultBlockParameter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DebugTraceCallResponse extends Response<JsonNode>{
    @JsonProperty("result")
    private JsonNode result;

    @JsonProperty("error")
    private Error error;

    // Getters and setters
    @Override
    public JsonNode getResult() {
        return result;
    }
    
    public void setResult(JsonNode result) {
        this.result = result;
    }
    
    // Helper method to get result as JSON string
    public String getResultAsJson() {
        return getResultAsJson(false);
    }
    
    // Helper method to get result as pretty JSON string
    public String getResultAsJson(boolean pretty) {
        if (result == null) return null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            if (pretty) {
                return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            } else {
                return mapper.writeValueAsString(result);
            }
        } catch (Exception e) {
            return result.toString();
        }
    }
    
    // Helper method to get result as specific type
    public <T> T getResultAs(Class<T> clazz) {
        if (result == null) return null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.treeToValue(result, clazz);
        } catch (Exception e) {
            return null;
        }
    }
}   
