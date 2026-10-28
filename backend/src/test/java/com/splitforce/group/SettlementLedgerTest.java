package com.splitforce.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.splitforce.expense.Expense;
import com.splitforce.expense.ExpenseRepository;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Runs against DynamoDB Local (docker compose up dynamodb-local); skipped when it isn't reachable.
 * Alice owes Bob 100.
 */
class SettlementLedgerTest {

    private static final String ALICE = "alice";
    private static final String BOB = "bob";

    private DynamoDbClient client;
    private DynamoDbTable<Expense> expensesTable;
    private DynamoDbTable<Group> groupsTable;
    private ExpenseRepository expenses;
    private SettlementLedger ledger;
    private Group group;

    @BeforeEach
    void setUp() {
        client = DynamoDbClient.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .region(Region.US_WEST_2)
                .endpointOverride(URI.create("http://localhost:8000"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .build();
        try {
            client.listTables();
        } catch (RuntimeException e) {
            assumeTrue(false, "DynamoDB Local not running on :8000");
        }
        DynamoDbEnhancedClient enhanced = DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
        String prefix = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "_";
        expensesTable = enhanced.table(prefix + "expenses", TableSchema.fromBean(Expense.class));
        groupsTable = enhanced.table(prefix + "groups", TableSchema.fromBean(Group.class));
        expensesTable.createTable();
        groupsTable.createTable();
        expenses = new ExpenseRepository(expensesTable);
        ledger = new SettlementLedger(client, expensesTable, groupsTable, expenses);

        group = new Group();
        group.setGroupId(UUID.randomUUID().toString());
        group.setBaseCurrency("INR");

        // Bob paid 200 split equally: Bob +100, Alice -100.
        Expense seed = new Expense();
        seed.setGroupId(group.getGroupId());
        seed.setExpenseId("2026-10-01#seed");
        seed.setDate("2026-10-01");
        seed.setCost(new BigDecimal("200.00"));
        seed.setCurrency("INR");
        seed.setPaidByUserId(BOB);
        seed.setShares(new LinkedHashMap<>(Map.of(BOB, new BigDecimal("100.00"), ALICE, new BigDecimal("-100.00"))));
        expenses.save(seed);
    }

    @AfterEach
    void tearDown() {
        if (expensesTable == null) return;
        expensesTable.deleteTable();
        groupsTable.deleteTable();
        client.close();
    }

    private Expense settlement(String from, String to, String amount) {
        BigDecimal a = new BigDecimal(amount);
        Expense e = new Expense();
        e.setGroupId(group.getGroupId());
        e.setDate("2026-10-28");
        e.setCategory("Payment");
        e.setPayment(true);
        e.setCost(a);
        e.setCurrency("INR");
        e.setPaidByUserId(from);
        e.setCreatedAt(Instant.now().toString());
        e.setShares(new LinkedHashMap<>(Map.of(from, a, to, a.negate())));
        return e;
    }

    private String settle(String requestKey, String from, String to, String amount) {
        return ledger.append(group.getGroupId(), requestKey, current -> {
            GroupController.requireOutstandingDebt(group, current, from, to, new BigDecimal(amount));
            return settlement(from, to, amount);
        });
    }

    private BigDecimal net(String user) {
        return GroupController.netByUser(group, expenses.listByGroupConsistent(group.getGroupId()))
                .getOrDefault(user, BigDecimal.ZERO);
    }

    /** Runs all tasks released at the same instant and returns their outcomes (id, or the thrown exception). */
    private List<Object> race(List<Callable<String>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<String> t : tasks) {
            futures.add(pool.submit(() -> {
                go.await();
                try {
                    return t.call();
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        go.countDown();
        List<Object> out = new ArrayList<>();
        for (Future<Object> f : futures) out.add(f.get());
        pool.shutdown();
        return out;
    }

    private static long successes(List<Object> results) {
        return results.stream().filter(r -> r instanceof String).count();
    }

    @Test
    void sequentialSecondSettlementOfSameDebtIsRejected() {
        settle("aaaaaaaa1", ALICE, BOB, "100.00");
        ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                () -> settle("bbbbbbbb2", ALICE, BOB, "100.00"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(0, net(ALICE).compareTo(BigDecimal.ZERO));
    }

    @Test
    void concurrentSettlementsOfSameDebtCommitExactlyOnce() throws Exception {
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String key = "req" + i;
            tasks.add(() -> settle(key, ALICE, BOB, "100.00"));
        }
        List<Object> results = race(tasks);

        assertEquals(1, successes(results), "results: " + results);
        for (Object r : results) {
            if (r instanceof Exception e) {
                assertTrue(e instanceof ResponseStatusException
                        && ((ResponseStatusException) e).getStatusCode() == HttpStatus.CONFLICT, "unexpected: " + e);
            }
        }
        assertEquals(0, net(ALICE).compareTo(BigDecimal.ZERO));
        assertEquals(0, net(BOB).compareTo(BigDecimal.ZERO));
    }

    @Test
    void sameRequestIdRetriedConcurrentlyIsIdempotent() throws Exception {
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) tasks.add(() -> settle("samekey-1", ALICE, BOB, "100.00"));
        List<Object> results = race(tasks);

        assertEquals(20, successes(results), "results: " + results);
        assertEquals(1, results.stream().distinct().count(), "every retry returns the same expense id");
        assertEquals(2, expenses.listByGroupConsistent(group.getGroupId()).size(), "seed + one settlement");
    }

    @Test
    void concurrentPartialPaymentsCannotExceedTheDebt() throws Exception {
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String key = "part" + i;
            tasks.add(() -> settle(key, ALICE, BOB, "30.00"));
        }
        List<Object> results = race(tasks);

        assertEquals(3, successes(results), "100 owed: three payments of 30 fit, a fourth would overpay. " + results);
        assertEquals(0, net(ALICE).compareTo(new BigDecimal("-10.00")));
    }
}
