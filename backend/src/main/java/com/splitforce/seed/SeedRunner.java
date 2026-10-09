package com.splitforce.seed;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.splitforce.expense.Expense;
import com.splitforce.expense.ExpenseRepository;
import com.splitforce.group.Group;
import com.splitforce.group.GroupMember;
import com.splitforce.group.GroupMemberRepository;
import com.splitforce.group.GroupRepository;
import com.splitforce.group.GroupType;
import com.splitforce.user.User;
import com.splitforce.user.UserRepository;

@Component
@Order(2)
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private record SeedGroup(String resource, String name, GroupType type) {}

    private static final List<SeedGroup> SEED = List.of(
            new SeedGroup("seed/kazakhstan-trip.csv", "Kazakhstan Trip", GroupType.trip),
            new SeedGroup("seed/flat-with-ashish.csv", "Flat with Ashish", GroupType.home),
            new SeedGroup("seed/madurai-trip.csv", "Madurai Trip", GroupType.trip),
            new SeedGroup("seed/college-general.csv", "College Group", GroupType.general),
            new SeedGroup("seed/ooty-trip.csv", "Ooty Trip", GroupType.trip),
            new SeedGroup("seed/khatu-shyam-trip.csv", "Khatu Shyam Trip", GroupType.trip),
            new SeedGroup("seed/vietnam-trip.csv", "Vietnam Trip", GroupType.trip),
            new SeedGroup("seed/andaman-trip.csv", "Andaman Trip", GroupType.trip)
    );

    /** Default FX rates in INR per 1 unit of foreign currency, applied when a
     *  seed CSV contains a currency other than the group's base and no rate has
     *  been set yet. Rough spot values — users can adjust in Settings. */
    private static final Map<String, BigDecimal> DEFAULT_FX_IN_INR = Map.of(
            "USD", new BigDecimal("84.00"),
            "EUR", new BigDecimal("92.00"),
            "GBP", new BigDecimal("106.00"),
            "AUD", new BigDecimal("56.00"),
            "JPY", new BigDecimal("0.57"),
            "VND", new BigDecimal("0.0033"),
            "KZT", new BigDecimal("0.17")
    );

    private final boolean enabled;
    private final String ownerEmail;
    private final String ownerName;
    private final UserRepository users;
    private final GroupRepository groups;
    private final GroupMemberRepository groupMembers;
    private final ExpenseRepository expenses;
    private final CsvExpenseParser parser;

    public SeedRunner(@Value("${app.seed.enabled:true}") boolean enabled,
                      @Value("${app.seed.owner-email}") String ownerEmail,
                      @Value("${app.seed.owner-name}") String ownerName,
                      UserRepository users,
                      GroupRepository groups,
                      GroupMemberRepository groupMembers,
                      ExpenseRepository expenses,
                      CsvExpenseParser parser) {
        this.enabled = enabled;
        this.ownerEmail = ownerEmail;
        this.ownerName = ownerName;
        this.users = users;
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.expenses = expenses;
        this.parser = parser;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Seeding disabled (app.seed.enabled=false)");
            return;
        }
        User owner = ensureUser(ownerName, ownerEmail, false);
        for (SeedGroup spec : SEED) {
            try {
                seedGroup(spec, owner);
            } catch (Exception e) {
                log.error("Failed to seed group {}: {}", spec.name(), e.getMessage(), e);
            }
        }
    }

    private void seedGroup(SeedGroup spec, User owner) throws Exception {
        String groupId = groupIdFor(spec.name());
        if (groups.findById(groupId).isPresent()) {
            log.info("Group '{}' already seeded (id={}); skipping", spec.name(), groupId);
            return;
        }

        ClassPathResource csv = new ClassPathResource(spec.resource());
        if (!csv.exists()) {
            log.info("Seed file {} not present; skipping group '{}'", spec.resource(), spec.name());
            return;
        }
        CsvExpenseParser.ParsedFile parsed;
        try (InputStream in = csv.getInputStream()) {
            parsed = parser.parse(in);
        }

        Group g = new Group();
        g.setGroupId(groupId);
        g.setName(spec.name());
        g.setType(spec.type().name());
        String baseCurrency = parsed.rows().stream()
                .map(CsvExpenseParser.ParsedRow::currency)
                .findFirst()
                .orElse("INR");
        g.setBaseCurrency(baseCurrency);
        g.setOwnerUserId(owner.getUserId());
        g.setCreatedAt(Instant.now().toString());

        Set<String> foreign = new LinkedHashSet<>();
        for (CsvExpenseParser.ParsedRow row : parsed.rows()) {
            if (row.currency() != null && !row.currency().equalsIgnoreCase(baseCurrency)) {
                foreign.add(row.currency().toUpperCase(Locale.ROOT));
            }
        }
        if (!foreign.isEmpty() && "INR".equalsIgnoreCase(baseCurrency)) {
            Map<String, BigDecimal> fx = new LinkedHashMap<>();
            for (String code : foreign) {
                BigDecimal rate = DEFAULT_FX_IN_INR.get(code);
                if (rate != null) fx.put(code, rate);
            }
            if (!fx.isEmpty()) g.setFxRates(fx);
        }

        groups.save(g);

        Map<String, String> headerToUserId = new HashMap<>();
        for (String rawHeader : parsed.memberHeaders()) {
            String displayName = cleanMemberName(rawHeader);
            boolean removed = rawHeader.contains("(removed)");
            String email = emailFor(displayName);
            User u = ensureUser(displayName, email, !email.equalsIgnoreCase(ownerEmail));
            headerToUserId.put(rawHeader, u.getUserId());

            GroupMember gm = new GroupMember();
            gm.setGroupId(groupId);
            gm.setUserId(u.getUserId());
            gm.setDisplayName(displayName);
            gm.setActive(!removed);
            gm.setJoinedAt(Instant.now().toString());
            groupMembers.save(gm);
        }

        int idx = 0;
        for (CsvExpenseParser.ParsedRow row : parsed.rows()) {
            idx++;
            Expense e = new Expense();
            e.setGroupId(groupId);
            e.setExpenseId(row.date().toString() + "#" + expenseIdFor(groupId, idx, row));
            e.setDate(row.date().toString());
            e.setDescription(row.description());
            e.setCategory(row.category());
            e.setCost(row.cost());
            e.setCurrency(row.currency());
            e.setPayment("Payment".equalsIgnoreCase(row.category()));

            Map<String, BigDecimal> shares = new HashMap<>();
            for (Map.Entry<String, BigDecimal> mn : row.memberNet().entrySet()) {
                String uid = headerToUserId.get(mn.getKey());
                if (uid != null && mn.getValue() != null) {
                    shares.put(uid, mn.getValue());
                }
            }
            e.setShares(shares);
            e.setCreatedAt(Instant.now().toString());
            expenses.save(e);
        }
        log.info("Seeded '{}' — {} members, {} expenses", spec.name(),
                parsed.memberHeaders().size(), parsed.rows().size());
    }

    private User ensureUser(String displayName, String email, boolean placeholder) {
        return users.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setUserId(userIdFor(email));
            u.setEmail(email);
            u.setName(displayName);
            u.setPlaceholder(placeholder);
            u.setCreatedAt(Instant.now().toString());
            users.save(u);
            return u;
        });
    }

    private String emailFor(String displayName) {
        if (displayName.equalsIgnoreCase(ownerName)) return ownerEmail;
        String slug = displayName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.+|\\.+$", "");
        return slug + "@placeholder.splitforce.local";
    }

    private static String cleanMemberName(String raw) {
        return raw.replaceAll("\\s*\\(.*\\)\\s*$", "").trim();
    }

    private static String userIdFor(String email) {
        return UUID.nameUUIDFromBytes(("user:" + email).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String groupIdFor(String name) {
        return UUID.nameUUIDFromBytes(("group:" + name).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String expenseIdFor(String groupId, int idx, CsvExpenseParser.ParsedRow row) {
        String key = groupId + ":" + idx + ":" + row.description() + ":" + row.cost();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
