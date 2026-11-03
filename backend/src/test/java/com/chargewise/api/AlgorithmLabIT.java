package com.chargewise.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chargewise.ApiTestSupport;
import com.chargewise.IntegrationTest;
import com.chargewise.repository.UserRepository;
import com.chargewise.service.demo.DemoDataSeeder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class AlgorithmLabIT {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DemoDataSeeder seeder;
    @Autowired private JdbcTemplate jdbc;

    private ApiTestSupport api;
    private String operator;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport(mvc, users, passwordEncoder, seeder, jdbc);
        operator = api.operator("lab-op@example.test");
    }

    @AfterEach
    void tearDown() {
        api.cleanUp();
    }

    private ResultActions lab(String path, String body) throws Exception {
        return mvc.perform(post("/api/v1/algorithms/" + path).header(HttpHeaders.AUTHORIZATION, operator)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void matchingEndpointReturnsGreedyCounterexampleCardinality2() throws Exception {
        // Greedy gives R1 its first connector C1, leaving R2 (compatible only with C1) unmatched.
        lab("matching/execute", """
                {"requests":["R1","R2"],"connectors":["C1","C2"],
                 "edges":[{"request":"R1","connector":"C1"},{"request":"R1","connector":"C2"},
                          {"request":"R2","connector":"C1"}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm.name").value("BIPARTITE_MATCHING"))
                .andExpect(jsonPath("$.algorithm.cardinality").value(2))
                .andExpect(jsonPath("$.algorithm.valid").value(true))
                .andExpect(jsonPath("$.algorithm.unmatched").isEmpty())
                .andExpect(jsonPath("$.algorithm.runtimeNs").isNumber())
                .andExpect(jsonPath("$.baseline.name").value("GREEDY_FIRST_AVAILABLE"))
                .andExpect(jsonPath("$.baseline.cardinality").value(1))
                .andExpect(jsonPath("$.baseline.pairs[0].request").value("R1"))
                .andExpect(jsonPath("$.baseline.pairs[0].connector").value("C1"))
                .andExpect(jsonPath("$.baseline.unmatched[0]").value("R2"));
    }

    @Test
    void maxFlowEndpointMatchesClrsExample() throws Exception {
        // Cormen et al., Introduction to Algorithms, Figure 26.1: maximum flow 23.
        lab("max-flow/execute", """
                {"network":{"nodes":["s","v1","v2","v3","v4","t"],"source":"s","sink":"t",
                 "edges":[{"from":"s","to":"v1","capacity":16},{"from":"s","to":"v2","capacity":13},
                          {"from":"v1","to":"v3","capacity":12},{"from":"v2","to":"v1","capacity":4},
                          {"from":"v2","to":"v4","capacity":14},{"from":"v3","to":"v2","capacity":9},
                          {"from":"v3","to":"t","capacity":20},{"from":"v4","to":"v3","capacity":7},
                          {"from":"v4","to":"t","capacity":4}]}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("NETWORK"))
                .andExpect(jsonPath("$.network.algorithm").value("EDMONDS_KARP"))
                .andExpect(jsonPath("$.network.totalFlow").value(23))
                .andExpect(jsonPath("$.network.minCutCapacity").value(23))
                .andExpect(jsonPath("$.network.edges.length()").value(9))
                .andExpect(jsonPath("$.power").doesNotExist());
    }

    @Test
    void maxFlowPowerModeComparesAgainstGreedyOnTheFeederCounterexample() throws Exception {
        // Station A is fed by F0 then F1, station B only by F0: greedy starves B, max-flow routes A via F1.
        lab("max-flow/execute", """
                {"power":{"stations":[{"id":"A","capacityKw":100},{"id":"B","capacityKw":100}],
                 "sessions":[{"id":"a1","station":"A","connectorMaxKw":150,"requestedKw":100,"vehicleMaxKw":150},
                             {"id":"b1","station":"B","connectorMaxKw":150,"requestedKw":100,"vehicleMaxKw":150}],
                 "feeders":[{"id":"F0","capacityKw":100},{"id":"F1","capacityKw":100}],
                 "links":[{"feeder":"F0","station":"A"},{"feeder":"F1","station":"A"},
                          {"feeder":"F0","station":"B"}]}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("POWER"))
                .andExpect(jsonPath("$.power.algorithm.name").value("EDMONDS_KARP"))
                .andExpect(jsonPath("$.power.algorithm.totalAllocatedKw").value(200.0))
                .andExpect(jsonPath("$.power.algorithm.violations").isEmpty())
                .andExpect(jsonPath("$.power.baseline.name").value("GREEDY_PRIORITY"))
                .andExpect(jsonPath("$.power.baseline.totalAllocatedKw").value(100.0))
                .andExpect(jsonPath("$.power.baseline.sessions[1].allocatedKw").value(0.0));
    }

    @Test
    void quickselectEndpointAgreesWithSort() throws Exception {
        lab("quickselect/execute", """
                {"values":[42,7,19,88,3,56,23,71,14,65],"percentiles":[50,90],"seed":7}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.n").value(10))
                .andExpect(jsonPath("$.agree").value(true))
                .andExpect(jsonPath("$.selections[0].label").value("p50"))
                .andExpect(jsonPath("$.selections[0].value").value(23))
                .andExpect(jsonPath("$.selections[1].value").value(71))
                .andExpect(jsonPath("$.algorithm.name").value("RANDOMIZED_QUICKSELECT"))
                .andExpect(jsonPath("$.baseline.name").value("FULL_SORT"));
        lab("quickselect/execute", """
                {"values":[5,1,4],"k":1,"seed":1}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selections[0].label").value("k=1"))
                .andExpect(jsonPath("$.selections[0].value").value(1));
        lab("quickselect/execute", """
                {"values":[5,1,4],"k":1,"percentiles":[50]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("k"));
        lab("quickselect/execute", """
                {"values":[5,1,4]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("k"));
    }

    @Test
    void quickselectDecimalPercentilesUseTheExactRank() throws Exception {
        String values = IntStream.rangeClosed(1, 1000).mapToObj(Integer::toString).collect(Collectors.joining(","));
        lab("quickselect/execute", "{\"values\":[" + values + "],\"percentiles\":[99.9,0.9],\"seed\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selections[0].label").value("p99.9"))
                .andExpect(jsonPath("$.selections[0].rank").value(998))
                .andExpect(jsonPath("$.selections[0].value").value(999))
                .andExpect(jsonPath("$.selections[1].rank").value(8))
                .andExpect(jsonPath("$.selections[1].value").value(9));
    }

    @Test
    void segmentTreeQueryMatchesNaive() throws Exception {
        lab("segment-tree/query", """
                {"values":[3,1,4,1,5,9,2,6],
                 "operations":[{"type":"SUM","from":0,"to":7},{"type":"MAX","from":2,"to":5},
                               {"type":"SET","index":5,"value":0},{"type":"MAX","from":2,"to":5},
                               {"type":"ADD","index":0,"value":10},{"type":"SUM","from":0,"to":1}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agree").value(true))
                .andExpect(jsonPath("$.results.length()").value(4))
                .andExpect(jsonPath("$.results[0].tree").value(31))
                .andExpect(jsonPath("$.results[1].tree").value(9))
                .andExpect(jsonPath("$.results[2].tree").value(5))
                .andExpect(jsonPath("$.results[3].tree").value(14))
                .andExpect(jsonPath("$.results[3].naive").value(14))
                .andExpect(jsonPath("$.algorithm.name").value("SEGMENT_TREE"))
                .andExpect(jsonPath("$.baseline.name").value("NAIVE_SCAN"));
        lab("segment-tree/query", """
                {"values":[1,2],"operations":[{"type":"SUM","from":1,"to":2}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void oversizedInputGives413() throws Exception {
        String requests = IntStream.rangeClosed(1, 5_001).mapToObj(i -> "\"R" + i + "\"")
                .collect(Collectors.joining(","));
        lab("matching/execute", "{\"requests\":[" + requests + "],\"connectors\":[],\"edges\":[]}")
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("INPUT_TOO_LARGE"));

        String values = IntStream.range(0, 1_000_001).mapToObj(i -> "1").collect(Collectors.joining(","));
        lab("quickselect/execute", "{\"values\":[" + values + "],\"k\":1}")
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("INPUT_TOO_LARGE"));
    }

    @Test
    void edgeCapacityAboveTheLimitGives400() throws Exception {
        // Two disjoint Long.MAX_VALUE paths would overflow the summed flow; capacities are capped so sums cannot.
        lab("max-flow/execute", """
                {"network":{"nodes":["s","a","b","t"],"source":"s","sink":"t",
                 "edges":[{"from":"s","to":"a","capacity":9223372036854775807},
                          {"from":"a","to":"t","capacity":9223372036854775807},
                          {"from":"s","to":"b","capacity":9223372036854775807},
                          {"from":"b","to":"t","capacity":9223372036854775807}]}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("network.edges[0].capacity"));
    }

    @Test
    void duplicateNodeIdsGive400() throws Exception {
        lab("max-flow/execute", """
                {"network":{"nodes":["s","a","a","t"],"source":"s","sink":"t",
                 "edges":[{"from":"s","to":"a","capacity":1},{"from":"a","to":"t","capacity":1}]}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("network.nodes"));
        lab("matching/execute", """
                {"requests":["R1"],"connectors":["C1"],"edges":[{"request":"R1","connector":"C9"}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("edges"));
    }
}
