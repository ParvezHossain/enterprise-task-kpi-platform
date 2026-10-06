package com.parvez.kpi.web;
import java.time.LocalDate;
import java.util.UUID;
import com.parvez.kpi.service.KpiQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/kpis")
public class KpiController {
    private final KpiQueryService queries;
    public KpiController(KpiQueryService queries){this.queries=queries;}
    @GetMapping({"/me","/team","/company"})
    public Object summary(@AuthenticationPrincipal Jwt jwt,jakarta.servlet.http.HttpServletRequest request,@RequestParam(required=false) UUID teamId,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to) {
        return queries.summary(jwt,request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/')+1),teamId,from,to);
    }
    @GetMapping({"/ranking","/top-performers"})
    public Object ranking(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) UUID teamId,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return queries.ranking(jwt,teamId,from,to,page,size);}
    @GetMapping("/trends")
    public Object trends(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="me") String scope,@RequestParam(required=false) UUID teamId,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="16") int size) {
        if(!java.util.Set.of("me","team","company").contains(scope)) throw new IllegalArgumentException("Invalid scope");return queries.trends(jwt,scope,teamId,from,to,page,size);
    }
    @GetMapping("/distribution")
    public Object distribution(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="me") String scope,@RequestParam(required=false) UUID teamId,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="16") int size) {
        if(!java.util.Set.of("me","team","company").contains(scope)) throw new IllegalArgumentException("Invalid scope");return queries.distribution(jwt,scope,teamId,from,to,page,size);
    }
}
