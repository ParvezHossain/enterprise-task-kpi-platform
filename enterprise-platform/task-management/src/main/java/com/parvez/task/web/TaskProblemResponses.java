package com.parvez.task.web;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
public final class TaskProblemResponses {
    private TaskProblemResponses() {}
    public static void write(HttpServletRequest request,HttpServletResponse response,int status,String title) throws IOException {
        response.setStatus(status);response.setContentType("application/problem+json");
        response.getWriter().write(new ObjectMapper().writeValueAsString(Map.of("type","about:blank","title",title,"status",status,"detail","The request could not be processed.","instance",request.getRequestURI())));
    }
}
